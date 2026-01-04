package com.medical.assistant.controller;

import com.medical.assistant.model.dto.DifyChatResponse;
import com.medical.assistant.service.DifyService;
import com.medical.assistant.service.TranscriptionService;
import com.medical.assistant.model.entity.Transcript;
import com.medical.assistant.repository.TranscriptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/dify")
@CrossOrigin(origins = "*")
public class DifyController {

    @Autowired
    private DifyService difyService;

    @Autowired
    private TranscriptionService transcriptionService;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithDify(@RequestParam String userInput,
                                     @RequestParam(required = false) String userId,
                                     @RequestParam(required = false) String conversationId) {
        log.info("【Dify对话接口】收到请求: userInput={}, userId={}", userInput, userId);
        
        return difyService.chatWithDify(userInput, userId, conversationId)
                .doOnNext(response -> log.info("【控制器】收到响应: event={}, answer={}", 
                    response.getEvent(), response.getAnswer() != null ? "有内容" : "无内容"))
                .map(response -> {
                    if (response.getAnswer() != null && !response.getAnswer().trim().isEmpty()) {
                        return "data: " + response.getAnswer() + "\n\n";
                    }
                    return "";
                })
                .filter(data -> !data.isEmpty())
                .doOnComplete(() -> log.info("【控制器】流处理完成"));
    }

    @PostMapping(value = "/chief-complaint/{visitId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateChiefComplaint(
            @PathVariable String visitId,
            @RequestParam String doctorId,
            @RequestParam String patientId) {

        log.info("【病情概要】开始生成，visitId: {}, doctorId: {}, patientId: {}",
                visitId, doctorId, patientId);

        try {
            // 获取最新的转录文本
            Transcript transcript = transcriptRepository.findLatestByVisitId(visitId)
                    .orElseThrow(() -> new RuntimeException("未找到visitId=" + visitId + "的转录记录"));

            log.info("【病情概要】找到最新转录记录，文本长度: {}", 
                    transcript.getTranscriptText() != null ? transcript.getTranscriptText().length() : 0);

            if (transcript.getTranscriptText() == null || transcript.getTranscriptText().isEmpty()) {
                return Flux.just("data: {\"event\": \"error\", \"message\": \"转录文本为空\"}\n\n");
            }
        } catch (Exception e) {
            log.error("【病情概要】获取转录记录失败", e);
            return Flux.just("data: {\"event\": \"error\", \"message\": \"" + e.getMessage() + "\"}\n\n");
        }

        try {
            // 获取最新的转录文本
            Transcript transcript = transcriptRepository.findLatestByVisitId(visitId).get();
            
            // 调用智能体生成病情概要
            return transcriptionService.generateChiefComplaintStream(
                            visitId,
                            transcript.getTranscriptText(),
                            doctorId,
                            patientId
                    )
                    .map(content -> {
                        if (content.equals("[COMPLETED]")) {
                            log.info("【病情概要】推送完成事件");
                            return "data: {\"event\": \"completed\", \"message\": \"病情概要生成完成\"}\n\n";
                        } else if (content.startsWith("[ERROR]")) {
                            log.error("【病情概要】推送错误事件: {}", content);
                            return "data: {\"event\": \"error\", \"message\": \"" + content.substring(7) + "\"}\n\n";
                        } else if (content.trim().startsWith("{")) {
                            // 这是JSON格式的病情概要（chief_complaint和notes），直接推送
                            log.info("【病情概要】推送JSON格式病情概要，长度: {}", content.length());
                            return "data: {\"event\": \"message\", \"content\": " + content.replace("\n", "").replace("  ", "") + "}\n\n";
                        } else {
                            // 普通文本内容
                            return "data: {\"event\": \"message\", \"content\": \"" +
                                    content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}\n\n";
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("【病情概要】生成失败，使用备用方案", error);
                        return Flux.just("data: {\"event\": \"error\", \"message\": \"" + error.getMessage() + "\"}\n\n");
                    });
        } catch (Exception e) {
            log.error("【病情概要】生成失败", e);
            return Flux.just("data: {\"event\": \"error\", \"message\": \"" + e.getMessage() + "\"}\n\n");
        }
    }
}