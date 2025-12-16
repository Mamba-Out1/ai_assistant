package com.medical.assistant.service.impl;

import com.medical.assistant.config.XunfeiConfig;
import com.medical.assistant.model.dto.TranscriptionRequest;
import com.medical.assistant.model.dto.TranscriptionResponse;
import com.medical.assistant.model.entity.TranscriptionRecord;
import com.medical.assistant.repository.TranscriptionRepository;
import com.medical.assistant.service.TranscriptionService;
import com.medical.assistant.util.SignatureUtil;
import com.medical.assistant.websocket.XunfeiWebSocketClient;
import com.medical.assistant.websocket.XunfeiWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class TranscriptionServiceImpl implements TranscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionServiceImpl.class);

    @Autowired
    private XunfeiConfig xunfeiConfig;

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    // 存储活动的WebSocket连接
    private final Map<String, XunfeiWebSocketClient> activeClients = new ConcurrentHashMap<>();

    @Override
    public TranscriptionResponse transcribeAudio(TranscriptionRequest request) throws Exception {
        logger.info("开始音频转写，用户ID: {}", request.getUserId());

        // 创建转写记录
        TranscriptionRecord record = createTranscriptionRecord(request);
        record.setStatus("PROCESSING");
        record = transcriptionRepository.save(record);
        final Long recordId = record.getId();

        // 生成WebSocket URL
        Map<String, String> params = buildWebSocketParams(request);
        String wsUrl = SignatureUtil.generateWebSocketUrl(
                xunfeiConfig.getWebsocketUrl(),
                xunfeiConfig.getAppId(),
                xunfeiConfig.getApiKey(),
                xunfeiConfig.getApiSecret(),
                params
        );

        // 创建WebSocket客户端
        final StringBuilder fullText = new StringBuilder();
        final String[] sessionId = {null};
        final Exception[] error = {null};

        XunfeiWebSocketHandler handler = new XunfeiWebSocketHandler() {
            @Override
            public void onConnected() {
                logger.info("WebSocket连接成功");
            }

            @Override
            public void onHandshakeSuccess(String sid) {
                sessionId[0] = sid;
                logger.info("握手成功，SessionID: {}", sid);

                // 更新记录的sessionId
                TranscriptionRecord updateRecord = transcriptionRepository.findById(recordId).orElse(null);
                if (updateRecord != null) {
                    updateRecord.setSessionId(sid);
                    transcriptionRepository.save(updateRecord);
                }
            }

            @Override
            public void onTranscriptionResult(String text, boolean isFinal) {
                logger.debug("转写结果: {} (isFinal: {})", text, isFinal);
                // 实时结果可以通过WebSocket推送给前端
            }

            @Override
            public void onTranscriptionComplete(String text) {
                fullText.append(text);
                logger.info("转写完成，文本长度: {}", text.length());

                // 保存到数据库
                TranscriptionRecord updateRecord = transcriptionRepository.findById(recordId).orElse(null);
                if (updateRecord != null) {
                    updateRecord.setTranscriptionText(text);
                    updateRecord.setStatus("COMPLETED");
                    transcriptionRepository.save(updateRecord);
                    logger.info("转写结果已保存到数据库，记录ID: {}", recordId);
                }
            }

            @Override
            public void onError(Exception e) {
                error[0] = e;
                logger.error("转写发生错误", e);

                // 更新记录状态
                TranscriptionRecord updateRecord = transcriptionRepository.findById(recordId).orElse(null);
                if (updateRecord != null) {
                    updateRecord.setStatus("FAILED");
                    updateRecord.setErrorMessage(e.getMessage());
                    transcriptionRepository.save(updateRecord);
                }
            }

            @Override
            public void onClosed(int code, String reason) {
                logger.info("WebSocket连接关闭: {}", reason);
            }
        };

        XunfeiWebSocketClient client = new XunfeiWebSocketClient(new URI(wsUrl), handler);

        // 连接WebSocket
        boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
        if (!connected) {
            throw new Exception("WebSocket连接超时");
        }

        // 等待握手完成
        Thread.sleep(500);

        // 分块发送音频数据
        int chunkSize = 1280; // 每次发送1280字节（40ms的音频，16k采样率）
        byte[] audioData = request.getAudioData();

        for (int i = 0; i < audioData.length; i += chunkSize) {
            int length = Math.min(chunkSize, audioData.length - i);
            byte[] chunk = Arrays.copyOfRange(audioData, i, i + length);
            client.sendAudio(chunk);

            // 模拟实时音频流，控制发送速度
            Thread.sleep(40);
        }

        // 发送结束标识
        client.sendEndFlag();

        // 等待转写完成
        boolean completed = client.awaitClose(30, TimeUnit.SECONDS);

        if (!completed) {
            // 处理超时情况
            logger.warn("转写超时");
            // 可以选择返回部分结果或抛出异常
        } else {
            // 正常处理结果
            logger.info("转写完成");
        }

        // 检查是否有错误
        if (error[0] != null) {
            throw error[0];
        }

        return TranscriptionResponse.success(sessionId[0], fullText.toString(), recordId);
    }

    @Override
    public String startStreamTranscription(TranscriptionRequest request) throws Exception {
        logger.info("开始流式转写，用户ID: {}", request.getUserId());

        // 创建转写记录
        TranscriptionRecord record = createTranscriptionRecord(request);
        record.setStatus("PROCESSING");
        record = transcriptionRepository.save(record);
        final Long recordId = record.getId();

        // 生成WebSocket URL
        Map<String, String> params = buildWebSocketParams(request);
        String wsUrl = SignatureUtil.generateWebSocketUrl(
                xunfeiConfig.getWebsocketUrl(),
                xunfeiConfig.getAppId(),
                xunfeiConfig.getApiKey(),
                xunfeiConfig.getApiSecret(),
                params
        );

        // 创建WebSocket客户端
        final String[] sessionId = {UUID.randomUUID().toString()};

        XunfeiWebSocketHandler handler = new XunfeiWebSocketHandler() {
            @Override
            public void onConnected() {
                logger.info("流式转写WebSocket连接成功");
            }

            @Override
            public void onHandshakeSuccess(String sid) {
                sessionId[0] = sid;
                logger.info("流式转写握手成功，SessionID: {}", sid);

                // 更新记录的sessionId
                TranscriptionRecord updateRecord = transcriptionRepository.findById(recordId).orElse(null);
                if (updateRecord != null) {
                    updateRecord.setSessionId(sid);
                    transcriptionRepository.save(updateRecord);
                }
            }

            @Override
            public void onTranscriptionResult(String text, boolean isFinal) {
                logger.debug("流式转写结果: {} (isFinal: {})", text, isFinal);
                // 可以实时推送给前端
            }

            @Override
            public void onTranscriptionComplete(String text) {
                logger.info("流式转写完成");

                // 保存到数据库
                TranscriptionRecord updateRecord = transcriptionRepository.findById(recordId).orElse(null);
                if (updateRecord != null) {
                    updateRecord.setTranscriptionText(text);
                    updateRecord.setStatus("COMPLETED");
                    transcriptionRepository.save(updateRecord);
                }
            }

            @Override
            public void onError(Exception e) {
                logger.error("流式转写发生错误", e);

                TranscriptionRecord updateRecord = transcriptionRepository.findById(recordId).orElse(null);
                if (updateRecord != null) {
                    updateRecord.setStatus("FAILED");
                    updateRecord.setErrorMessage(e.getMessage());
                    transcriptionRepository.save(updateRecord);
                }
            }

            @Override
            public void onClosed(int code, String reason) {
                logger.info("流式转写WebSocket连接关闭: {}", reason);
                activeClients.remove(sessionId[0]);
            }
        };

        XunfeiWebSocketClient client = new XunfeiWebSocketClient(new URI(wsUrl), handler);

        // 连接WebSocket
        boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
        if (!connected) {
            throw new Exception("WebSocket连接超时");
        }

        // 等待握手完成
        Thread.sleep(500);

        // 保存客户端
        activeClients.put(sessionId[0], client);

        return sessionId[0];
    }

    @Override
    public void sendAudioStream(String sessionId, byte[] audioData) throws Exception {
        XunfeiWebSocketClient client = activeClients.get(sessionId);
        if (client == null) {
            throw new Exception("未找到对应的WebSocket连接: " + sessionId);
        }

        if (!client.isOpen()) {
            throw new Exception("WebSocket连接已关闭");
        }

        client.sendAudio(audioData);
    }

    @Override
    public TranscriptionResponse endStreamTranscription(String sessionId) throws Exception {
        XunfeiWebSocketClient client = activeClients.get(sessionId);
        if (client == null) {
            throw new Exception("未找到对应的WebSocket连接: " + sessionId);
        }

        // 发送结束标识
        client.sendEndFlag();

        // 等待转写完成（最多等待30秒）
        boolean closed = client.awaitClose(30, TimeUnit.SECONDS);
        if (!closed) {
            logger.warn("等待转写完成超时");
        }

        // 获取完整转写结果
        String fullText = client.getFullTranscription();

        // 从活动连接中移除
        activeClients.remove(sessionId);

        // 查询数据库记录
        TranscriptionRecord record = transcriptionRepository.findBySessionId(sessionId).orElse(null);
        Long recordId = record != null ? record.getId() : null;

        return TranscriptionResponse.success(sessionId, fullText, recordId);
    }

    @Override
    public TranscriptionRecord getTranscriptionBySessionId(String sessionId) {
        return transcriptionRepository.findBySessionId(sessionId).orElse(null);
    }

    @Override
    public List<TranscriptionRecord> getTranscriptionsByUserId(String userId) {
        return transcriptionRepository.findByUserId(userId);
    }

    @Override
    public TranscriptionRecord saveTranscriptionRecord(TranscriptionRecord record) {
        return transcriptionRepository.save(record);
    }

    /**
     * 创建转写记录
     */
    private TranscriptionRecord createTranscriptionRecord(TranscriptionRequest request) {
        TranscriptionRecord record = new TranscriptionRecord();
        record.setUserId(request.getUserId());
        record.setAudioFormat(request.getAudioEncode());
        record.setSampleRate(request.getSampleRate());
        record.setLanguage(request.getLang());
        record.setStatus("CREATED");

        // 计算音频时长（秒）
        if (request.getAudioData() != null && request.getSampleRate() != null) {
            int duration = request.getAudioData().length / (request.getSampleRate() * 2); // 16bit=2字节
            record.setAudioDuration(duration);
        }

        return record;
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
        if (request.getRoleType() != null) {
            params.put("role_type", String.valueOf(request.getRoleType()));
        }

        // 领域参数
        if (request.getPd() != null) {
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

        // UUID
        params.put("uuid", UUID.randomUUID().toString().replace("-", ""));

        return params;
    }
}