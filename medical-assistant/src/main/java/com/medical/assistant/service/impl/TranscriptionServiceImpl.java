package com.medical.assistant.service.impl;

import com.medical.assistant.config.XunfeiConfig;
import com.medical.assistant.model.dto.TranscriptionRequest;
import com.medical.assistant.model.dto.TranscriptionResponse;
import com.medical.assistant.model.dto.MedicalSummaryDto;
import com.medical.assistant.model.entity.Transcript;
import com.medical.assistant.model.entity.MedicalSummary;
import com.medical.assistant.repository.TranscriptRepository;
import com.medical.assistant.repository.MedicalSummaryRepository;
import com.medical.assistant.repository.VisitRepository;
import com.medical.assistant.service.TranscriptionService;
import com.medical.assistant.service.DifyService;
import com.medical.assistant.util.SignatureUtil;
import com.medical.assistant.websocket.XunfeiWebSocketClient;
import com.medical.assistant.websocket.XunfeiWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TranscriptionServiceImpl implements TranscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionServiceImpl.class);

    // 音频发送配置（与官方示例一致）
    private static final int AUDIO_FRAME_SIZE = 1280;  // 每帧字节数
    private static final int FRAME_INTERVAL_MS = 40;   // 帧间隔(毫秒)
    private static final int SERVER_READY_WAIT_MS = 1500; // 等待服务端就绪时间

    @Autowired
    private XunfeiConfig xunfeiConfig;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private MedicalSummaryRepository medicalSummaryRepository;

    @Autowired
    private DifyService difyService;

    @Autowired
    private VisitRepository visitRepository;

    // 存储活动的WebSocket连接
    private final Map<String, XunfeiWebSocketClient> activeClients = new ConcurrentHashMap<>();

    @Override
    public TranscriptionResponse transcribeAudio(TranscriptionRequest request) throws Exception {
        logger.info("【开始转写】用户ID: {}, visitId: {}", request.getUserId(), request.getVisitId());

        // 构建WebSocket参数
        Map<String, String> params = buildWebSocketParams(request);

        // 生成WebSocket URL
        String wsUrl = SignatureUtil.generateWebSocketUrl(
                xunfeiConfig.getWebsocketUrl(),
                xunfeiConfig.getAppId(),
                xunfeiConfig.getApiKey(),
                xunfeiConfig.getApiSecret(),
                params
        );

        // 用于同步的变量
        final String[] sessionId = {null};
        final String[] finalTranscriptText = {""};
        final Exception[] error = {null};
        final AtomicBoolean handshakeComplete = new AtomicBoolean(false);
        final CountDownLatch completeLatch = new CountDownLatch(1);

        // 创建WebSocket处理器
        XunfeiWebSocketHandler handler = new XunfeiWebSocketHandler() {
            @Override
            public void onConnected() {
                logger.info("【WebSocket】连接成功");
            }

            @Override
            public void onHandshakeSuccess(String sid) {
                sessionId[0] = sid;
                handshakeComplete.set(true);
                logger.info("【WebSocket】握手成功，SessionID: {}", sid);
            }

            @Override
            public void onTranscriptionResult(String text, boolean isFinal) {
                logger.debug("【转写结果】{} (确定性: {})", text, isFinal);
            }

            @Override
            public void onTranscriptionComplete(String fullText) {
                logger.info("【转写完成】文本长度: {}", fullText.length());
                finalTranscriptText[0] = fullText;
                completeLatch.countDown();
            }

            @Override
            public void onError(Exception e) {
                error[0] = e;
                logger.error("【转写错误】{}", e.getMessage());
                completeLatch.countDown();
            }

            @Override
            public void onClosed(int code, String reason) {
                logger.info("【WebSocket】连接关闭: code={}, reason={}", code, reason);
                completeLatch.countDown();
            }
        };

        // 创建WebSocket客户端
        XunfeiWebSocketClient client = new XunfeiWebSocketClient(new URI(wsUrl), handler);

        try {
            // 1. 建立连接
            logger.info("【步骤1】建立WebSocket连接...");
            boolean connected = client.connectBlocking(15, TimeUnit.SECONDS);
            if (!connected) {
                throw new Exception("WebSocket连接超时");
            }

            // 2. 等待服务端就绪（关键！）
            logger.info("【步骤2】等待服务端就绪（{}ms）...", SERVER_READY_WAIT_MS);
            Thread.sleep(SERVER_READY_WAIT_MS);

            // 检查是否有错误
            if (error[0] != null) {
                throw error[0];
            }

            // 3. 发送音频数据
            logger.info("【步骤3】开始发送音频数据...");
            sendAudioWithPreciseTiming(client, request.getAudioData());

            // 4. 发送结束标记
            logger.info("【步骤4】发送结束标记...");
            client.sendEndFlag();

            // 5. 等待转写完成
            byte[] audioData = request.getAudioData();
            long totalFrames = audioData.length / AUDIO_FRAME_SIZE;
            double estimatedDuration = (totalFrames * FRAME_INTERVAL_MS) / 1000.0;
            int waitTime = (int) estimatedDuration + 10; // 额外等待10秒

            logger.info("【步骤5】等待转写完成，预估时长: {:.1f}秒，等待: {}秒...", estimatedDuration, waitTime);

            boolean completed = completeLatch.await(waitTime, TimeUnit.SECONDS);
            if (!completed) {
                logger.warn("【超时】等待转写完成超时，但继续返回已有结果");
            }

            // 检查是否有错误
            if (error[0] != null) {
                throw error[0];
            }

            // 获取转写结果
            String fullText = client.getFullTranscription();

            // 如果有visitId，保存转录结果到transcripts表
            if (request.getVisitId() != null && !request.getVisitId().isEmpty()) {
                // 计算音频时长
                int audioDuration = audioData.length / (request.getSampleRate() * 2); // 16bit=2字节

                Transcript transcript = saveTranscript(
                        request.getVisitId(),
                        fullText,
                        audioDuration,
                        request.getAudioEncode()
                );
                logger.info("【数据库】转录结果已保存到transcripts表，ID: {}", transcript.getTranscriptId());
            }

            // 返回响应
            return TranscriptionResponse.success(sessionId[0], fullText, null);

        } finally {
            // 确保关闭连接
            if (client.isOpen()) {
                client.close();
            }
        }
    }

    /**
     * 精确节奏控制发送音频（核心方法）
     */
    private void sendAudioWithPreciseTiming(XunfeiWebSocketClient client, byte[] audioData) throws Exception {
        // 计算总帧数和预估时长
        long totalFrames = audioData.length / AUDIO_FRAME_SIZE;
        long remainingBytes = audioData.length % AUDIO_FRAME_SIZE;
        if (remainingBytes > 0) {
            totalFrames++;
        }
        double estimatedDuration = (totalFrames * FRAME_INTERVAL_MS) / 1000.0;

        logger.info("【发送配置】音频大小: {} bytes | 总帧数: {} | 预估时长: {:.1f}秒",
                audioData.length, totalFrames, estimatedDuration);
        logger.info("【发送配置】每{}ms发送{}字节，严格控制节奏", FRAME_INTERVAL_MS, AUDIO_FRAME_SIZE);

        // 发送音频帧
        int frameIndex = 0;
        Long startTime = null;

        for (int offset = 0; offset < audioData.length; offset += AUDIO_FRAME_SIZE) {
            // 检查连接状态
            if (!client.isConnected() || !client.isOpen()) {
                throw new Exception("WebSocket连接已断开");
            }

            // 计算当前帧大小（最后一帧可能不足AUDIO_FRAME_SIZE）
            int frameSize = Math.min(AUDIO_FRAME_SIZE, audioData.length - offset);
            byte[] frameData = Arrays.copyOfRange(audioData, offset, offset + frameSize);

            // 记录起始时间
            if (startTime == null) {
                startTime = System.currentTimeMillis();
                logger.info("【发送开始】起始时间: {}ms（基准时间）", startTime);
            }

            // 计算理论发送时间
            long expectedSendTime = startTime + ((long) frameIndex * FRAME_INTERVAL_MS);
            long currentTime = System.currentTimeMillis();
            long timeDiff = expectedSendTime - currentTime;

            // 动态调整休眠时间（保持节奏）
            if (timeDiff > 1) {
                Thread.sleep(timeDiff);
            }

            // 发送音频帧
            client.sendAudioFrame(frameData);

            // 打印节奏控制日志（每25帧，约1秒）
            if (frameIndex % 25 == 0) {
                long actualSendTime = System.currentTimeMillis();
                logger.debug("【节奏控制】帧{} | 理论时间: {}ms | 实际时间: {}ms | 误差: {:.1f}ms",
                        frameIndex, expectedSendTime, actualSendTime,
                        (actualSendTime - expectedSendTime) * 1.0);
            }

            frameIndex++;
        }

        logger.info("【发送完成】所有音频帧发送完毕（共{}帧）", frameIndex);
    }

    @Override
    public Transcript saveTranscript(String visitId, String transcriptText, Integer audioDuration, String audioFormat) {
        try {
            Transcript transcript = new Transcript();
            transcript.setTranscriptId(UUID.randomUUID().toString());
            transcript.setVisitId(visitId);
            transcript.setTranscriptText(transcriptText);
            transcript.setAudioDuration(audioDuration);
            transcript.setAudioFormat(audioFormat);
            transcript.setStatus(Transcript.TranscriptStatus.COMPLETED);

            return transcriptRepository.save(transcript);
        } catch (Exception e) {
            logger.error("保存转录结果失败", e);
            throw new RuntimeException("保存转录结果失败", e);
        }
    }

    @Override
    public Flux<String> generateMedicalSummaryStream(String visitId, String transcriptText,
                                                     String doctorId, String patientId) {

        // 获取或创建会话ID
        String conversationId = visitId; // 使用visitId作为会话ID

        // 用于收集完整响应
        StringBuilder completeResponse = new StringBuilder();

        return difyService.generateMedicalSummaryStream(transcriptText, doctorId, conversationId)
                .map(response -> {
                    String content = "";

                    if ("message".equals(response.getEvent()) && response.getAnswer() != null) {
                        content = response.getAnswer();
                        completeResponse.append(content);
                    } else if ("message_end".equals(response.getEvent())) {
                        // 消息结束，保存病历总结
                        try {
                            MedicalSummaryDto summaryDto = difyService.extractMedicalSummary(completeResponse.toString());
                            summaryDto.setVisitId(visitId);
                            summaryDto.setDoctorId(doctorId);
                            summaryDto.setPatientId(patientId);
                            summaryDto.setRawResponse(completeResponse.toString());

                            saveMedicalSummary(summaryDto);
                            content = "[COMPLETED]";
                        } catch (Exception e) {
                            logger.error("保存病历总结失败", e);
                            content = "[ERROR]" + e.getMessage();
                        }
                    }

                    return content;
                })
                .filter(content -> !content.isEmpty());
    }

    @Override
    public void saveMedicalSummary(MedicalSummaryDto summaryDto) {
        try {
            MedicalSummary summary = new MedicalSummary();
            summary.setSummaryId(UUID.randomUUID().toString());
            summary.setVisitId(summaryDto.getVisitId());
            summary.setDoctorId(summaryDto.getDoctorId());
            summary.setPatientId(summaryDto.getPatientId());
            summary.setSymptomDetails(summaryDto.getSymptomDetails());
            summary.setVitalSigns(summaryDto.getVitalSigns());
            summary.setPastMedicalHistory(summaryDto.getPastMedicalHistory());
            summary.setCurrentMedications(summaryDto.getCurrentMedications());

            medicalSummaryRepository.save(summary);
            logger.info("【数据库】病历总结已保存，summaryId: {}", summary.getSummaryId());

        } catch (Exception e) {
            logger.error("保存病历总结失败", e);
            throw new RuntimeException("保存病历总结失败", e);
        }
    }

    @Override
    public String startStreamTranscription(TranscriptionRequest request) throws Exception {
        logger.info("【流式转写】开始，用户ID: {}", request.getUserId());

        // 生成临时sessionId
        String tempSessionId = UUID.randomUUID().toString();

        // 构建WebSocket参数
        Map<String, String> params = buildWebSocketParams(request);

        // 生成WebSocket URL
        String wsUrl = SignatureUtil.generateWebSocketUrl(
                xunfeiConfig.getWebsocketUrl(),
                xunfeiConfig.getAppId(),
                xunfeiConfig.getApiKey(),
                xunfeiConfig.getApiSecret(),
                params
        );

        // 创建WebSocket处理器
        XunfeiWebSocketHandler handler = new XunfeiWebSocketHandler() {
            @Override
            public void onConnected() {
                logger.info("【流式WebSocket】连接成功");
            }

            @Override
            public void onHandshakeSuccess(String sid) {
                logger.info("【流式WebSocket】握手成功，SessionID: {}", sid);
            }

            @Override
            public void onTranscriptionResult(String text, boolean isFinal) {
                logger.debug("【流式结果】{} (确定性: {})", text, isFinal);
            }

            @Override
            public void onTranscriptionComplete(String fullText) {
                logger.info("【流式完成】文本长度: {}", fullText.length());
                // 如果有visitId，保存转录结果
                if (request.getVisitId() != null && !request.getVisitId().isEmpty()) {
                    try {
                        saveTranscript(request.getVisitId(), fullText, null, request.getAudioEncode());
                    } catch (Exception e) {
                        logger.error("保存流式转录结果失败", e);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                logger.error("【流式错误】{}", e.getMessage());
            }

            @Override
            public void onClosed(int code, String reason) {
                logger.info("【流式WebSocket】连接关闭");
                activeClients.remove(tempSessionId);
            }
        };

        // 创建并连接WebSocket客户端
        XunfeiWebSocketClient client = new XunfeiWebSocketClient(new URI(wsUrl), handler);

        boolean connected = client.connectBlocking(15, TimeUnit.SECONDS);
        if (!connected) {
            throw new Exception("WebSocket连接超时");
        }

        // 等待服务端就绪
        Thread.sleep(SERVER_READY_WAIT_MS);

        // 保存客户端
        activeClients.put(tempSessionId, client);

        return tempSessionId;
    }

    @Override
    public void sendAudioStream(String sessionId, byte[] audioData) throws Exception {
        XunfeiWebSocketClient client = activeClients.get(sessionId);
        if (client == null) {
            throw new Exception("未找到对应的WebSocket连接: " + sessionId);
        }

        if (!client.isConnected() || !client.isOpen()) {
            throw new Exception("WebSocket连接已关闭");
        }

        // 直接发送音频数据
        client.sendAudioFrame(audioData);
    }

    @Override
    public TranscriptionResponse endStreamTranscription(String sessionId) throws Exception {
        XunfeiWebSocketClient client = activeClients.get(sessionId);
        if (client == null) {
            throw new Exception("未找到对应的WebSocket连接: " + sessionId);
        }

        // 发送结束标记
        client.sendEndFlag();

        // 等待转写完成
        boolean closed = client.awaitClose(30, TimeUnit.SECONDS);
        if (!closed) {
            logger.warn("等待转写完成超时");
        }

        // 获取结果
        String fullText = client.getFullTranscription();

        // 移除客户端
        activeClients.remove(sessionId);

        return TranscriptionResponse.success(sessionId, fullText, null);
    }

    @Override
    public Transcript getTranscriptionBySessionId(String sessionId) {
        // 这个方法可能需要调整，因为Transcript表使用的是transcriptId而不是sessionId
        // 可以通过visitId查询
        return null; // 暂时返回null，根据实际需求实现
    }

    @Override
    public List<Transcript> getTranscriptionsByUserId(String userId) {
        // 需要通过visits表关联查询
        // 这里需要根据实际需求实现
        return new ArrayList<>();
    }

    @Override
    public Transcript saveTranscriptionRecord(Transcript record) {
        return transcriptRepository.save(record);
    }

    /**
     * 构建WebSocket参数
     */
    private Map<String, String> buildWebSocketParams(TranscriptionRequest request) {
        Map<String, String> params = new HashMap<>();

        // 音频编码
        params.put("audio_encode", request.getAudioEncode());

        // 采样率（仅pcm格式需要）
        if ("pcm_s16le".equals(request.getAudioEncode())) {
            params.put("samplerate", String.valueOf(request.getSampleRate()));
        }

        // 语言
        params.put("lang", request.getLang());

        // 说话人分离
        if (request.getRoleType() != null && request.getRoleType() > 0) {
            params.put("role_type", String.valueOf(request.getRoleType()));
        }

        // 领域参数
        if (request.getPd() != null && !request.getPd().isEmpty()) {
            params.put("pd", request.getPd());
        }

        // 标点控制
        if (request.getEngPunc() != null) {
            params.put("eng_punc", String.valueOf(request.getEngPunc()));
        }

        // VAD远近场
        if (request.getEngVadMdn() != null) {
            params.put("eng_vad_mdn", String.valueOf(request.getEngVadMdn()));
        }

        return params;
    }
}
