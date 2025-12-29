package com.medical.assistant.controller;

import com.medical.assistant.service.TextToSpeechService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tts")
@CrossOrigin(origins = "*")
public class TextToSpeechController {

    @Autowired
    private TextToSpeechService ttsService;

    /**
     * 语音播报接口
     */
    @PostMapping("/speak")
    public ResponseEntity<Map<String, String>> textToSpeech(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            if (text == null || text.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("status", "ERROR");
                error.put("message", "文本内容不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            // 调用TTS服务处理
            String result = ttsService.processTextToSpeech(text);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", result);
            response.put("text", text);
            response.put("audioUrl", "/api/tts/audio?text=" + java.net.URLEncoder.encode(text, "UTF-8"));
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("语音播报失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 生成提示音频
     */
    @GetMapping("/audio")
    public ResponseEntity<byte[]> generateAudio(@RequestParam String text) {
        try {
            byte[] audioData = ttsService.generateNotificationSound();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/wav"));
            headers.setContentLength(audioData.length);
            
            return ResponseEntity.ok().headers(headers).body(audioData);
            
        } catch (Exception e) {
            log.error("生成音频失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}