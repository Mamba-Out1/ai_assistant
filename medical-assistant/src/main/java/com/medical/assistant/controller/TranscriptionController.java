package com.medical.assistant.controller;

import com.medical.assistant.model.dto.TranscriptionRequest;
import com.medical.assistant.model.dto.TranscriptionResponse;
import com.medical.assistant.model.entity.Transcript;
import com.medical.assistant.service.TranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transcription")
@CrossOrigin(origins = "*")
public class TranscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionController.class);

    @Autowired
    private TranscriptionService transcriptionService;

    /**
     * 上传音频文件进行转写
     */
    @PostMapping("/upload")
    public ResponseEntity<TranscriptionResponse> uploadAndTranscribe(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "audioEncode", defaultValue = "pcm_s16le") String audioEncode,
            @RequestParam(value = "sampleRate", defaultValue = "16000") Integer sampleRate,
            @RequestParam(value = "lang", defaultValue = "autodialect") String lang) {

        try {
            logger.info("收到音频文件上传请求，文件名: {}, 大小: {} bytes",
                    file.getOriginalFilename(), file.getSize());

            // 构建请求
            TranscriptionRequest request = new TranscriptionRequest();
            request.setAudioData(file.getBytes());
            request.setUserId(userId);
            request.setAudioEncode(audioEncode);
            request.setSampleRate(sampleRate);
            request.setLang(lang);

            // 执行转写
            TranscriptionResponse response = transcriptionService.transcribeAudio(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("音频转写失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TranscriptionResponse.error(null, e.getMessage()));
        }
    }

    /**
     * 开始流式转写
     */
    @PostMapping("/stream/start")
    public ResponseEntity<Map<String, String>> startStreamTranscription(
            @Valid @RequestBody TranscriptionRequest request) {

        try {
            logger.info("开始流式转写，用户ID: {}", request.getUserId());

            String sessionId = transcriptionService.startStreamTranscription(request);

            Map<String, String> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("status", "STARTED");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("启动流式转写失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 发送音频流数据
     */
    @PostMapping("/stream/send/{sessionId}")
    public ResponseEntity<Map<String, String>> sendAudioStream(
            @PathVariable String sessionId,
            @RequestBody byte[] audioData) {

        try {
            transcriptionService.sendAudioStream(sessionId, audioData);

            Map<String, String> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "音频数据已发送");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("发送音频流失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 结束流式转写
     */
    @PostMapping("/stream/end/{sessionId}")
    public ResponseEntity<TranscriptionResponse> endStreamTranscription(
            @PathVariable String sessionId) {

        try {
            logger.info("结束流式转写，sessionId: {}", sessionId);

            TranscriptionResponse response = transcriptionService.endStreamTranscription(sessionId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("结束流式转写失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TranscriptionResponse.error(sessionId, e.getMessage()));
        }
    }

    /**
     * 查询转写记录
     */
    @GetMapping("/record/{sessionId}")
    public ResponseEntity<Transcript> getTranscriptionRecord(
            @PathVariable String sessionId) {

        try {
            Transcript record = transcriptionService.getTranscriptionBySessionId(sessionId);

            if (record == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(record);

        } catch (Exception e) {
            logger.error("查询转写记录失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询用户的所有转写记录
     */
    @GetMapping("/records/user/{userId}")
    public ResponseEntity<List<Transcript>> getUserTranscriptions(
            @PathVariable String userId) {

        try {
            List<Transcript> records = transcriptionService.getTranscriptionsByUserId(userId);
            return ResponseEntity.ok(records);

        } catch (Exception e) {
            logger.error("查询用户转写记录失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "transcription");
        return ResponseEntity.ok(status);
    }
}