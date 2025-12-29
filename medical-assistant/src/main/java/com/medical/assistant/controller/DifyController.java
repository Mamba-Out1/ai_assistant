package com.medical.assistant.controller;

import com.medical.assistant.model.dto.DifyChatResponse;
import com.medical.assistant.service.DifyService;
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
}