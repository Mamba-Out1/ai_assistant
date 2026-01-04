package com.medical.assistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.assistant.config.DifyConfig;
import com.medical.assistant.model.dto.DifyChatRequest;
import com.medical.assistant.model.dto.DifyChatResponse;
import com.medical.assistant.model.dto.MedicalSummaryDto;
import com.medical.assistant.service.DifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class DifyServiceImpl implements DifyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    // 规范化后的 API Key（不包含 Bearer 前缀）
    private String normalizedApiKey;

    @Autowired
    private DifyConfig difyConfig;

    public DifyServiceImpl(DifyConfig difyConfig, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 保证构造器注入的配置被赋值给字段（避免混用注入导致 field 未设置）
        this.difyConfig = difyConfig;

        String rawApiKey = difyConfig.getApiKey() == null ? "" : difyConfig.getApiKey().trim();
        // 如果用户在配置中已经包含了 'Bearer ' 前缀，去掉以避免重复
        this.normalizedApiKey = rawApiKey.startsWith("Bearer ") ? rawApiKey.substring(7) : rawApiKey;

        if (this.normalizedApiKey.isEmpty()) {
            log.warn("【Dify配置】未配置 api-key，Dify 调用将失败。请在配置中设置 dify.api-key 或通过环境变量覆盖。");
        } else {
            log.info("【Dify配置】API URL: {}, API Key: {}***", 
                    difyConfig.getApiUrl(), 
                    this.normalizedApiKey.substring(0, Math.min(10, this.normalizedApiKey.length())));
        }

        this.webClient = WebClient.builder()
                .baseUrl(difyConfig.getApiUrl())
                // 设置默认 Authorization header，确保每次请求都携带 Bearer token
                .defaultHeader("Authorization", "Bearer " + this.normalizedApiKey)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        
        log.debug("【Dify API 调试】Authorization Header: Bearer {}***", 
                normalizedApiKey.substring(0, Math.min(10, normalizedApiKey.length())));

        // 打印完整请求头（仅用于调试，生产环境请移除）
        webClient.mutate()
                .defaultHeader("Authorization", "Bearer " + normalizedApiKey)
                .build();
    }
    

    @Override
    public Flux<DifyChatResponse> generateMedicalSummaryStream(String transcriptText, String userId, String conversationId) {
        log.info("【Dify API】开始调用，文本长度: {}, 用户ID: {}", transcriptText.length(), userId);
        
        if (this.normalizedApiKey == null || this.normalizedApiKey.isEmpty()) {
            log.error("【Dify API】未配置 API Key，停止调用 Dify。请设置 dify.api-key 或通过环境变量注入。返回友好错误响应。");
            return Flux.just(createErrorResponse("Dify API key 未配置"));
        }

        // 构建请求（严格按照Dify API文档格式）
        DifyChatRequest request = new DifyChatRequest();
        request.setInputs(new HashMap<>()); 
        request.setQuery("请根据以下语音转录内容生成病历总结：" + transcriptText);
        request.setResponseMode("streaming");
        request.setConversationId(""); 
        request.setUser(userId != null ? userId : "medical_assistant");
        request.setFiles(null); // 不传文件

        try {
            log.info("【Dify API】请求参数: query={}, user={}, responseMode={}, JSON={}", 
                    request.getQuery().substring(0, Math.min(50, request.getQuery().length())) + "...",
                    request.getUser(), request.getResponseMode(),
                    objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.error("序列化请求失败", e);
        }

        // 调用Dify API
        return webClient.post()
                .uri("/chat-messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                    response -> {
                        return response.bodyToMono(String.class)
                            .map(errorBody -> {
                                log.error("【Dify API】调用失败: {} - {}", response.statusCode(), errorBody);
                                return new RuntimeException("Dify API调用失败: " + response.statusCode() + " - " + errorBody);
                            });
                    })
                .bodyToFlux(String.class)
                .timeout(java.time.Duration.ofSeconds(30))
                .doOnSubscribe(s -> log.info("【Dify API】开始订阅响应流"))
                .doOnNext(data -> {
                    log.info("【Dify API】收到原始数据: {}", data);
                })
                .doOnError(error -> log.error("【Dify API】流处理错误", error))
                .doOnComplete(() -> log.info("【Dify API】流处理完成"))
                .filter(data -> {
                    // 处理两种格式：
                    // 1. SSE格式: "data: {...}"
                    // 2. 直接JSON: "{...}"
                    String trimmed = data.trim();
                    if (trimmed.isEmpty() || trimmed.equals("[DONE]")) {
                        return false;
                    }
                    if (trimmed.equals("data: [DONE]")) {
                        return false;
                    }
                    // 保留"data: "开头和直接JSON的数据
                    return trimmed.startsWith("data: ") || trimmed.startsWith("{");
                })
                .map(data -> {
                    try {
                        String jsonStr = data.trim();
                        
                        // 移除 "data: " 前缀（如果有）
                        if (jsonStr.startsWith("data: ")) {
                            jsonStr = jsonStr.substring(6);
                        }
                        
                        // 解析Dify响应
                        DifyChatResponse response = objectMapper.readValue(jsonStr, DifyChatResponse.class);
                        
                        // 处理HTML实体转义
                        if (response.getAnswer() != null) {
                            String unescapedJson = response.getAnswer()
                                    .replace("&quot;", "\"")
                                    .replace("&amp;", "&")
                                    .replace("&lt;", "<")
                                    .replace("&gt;", ">")
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"");
                            response.setAnswer(unescapedJson);
                        }
                        
                        log.info("【Dify API】解析成功: event={}, answer存在={}", 
                                response.getEvent(), response.getAnswer() != null);
                        return response;
                    } catch (Exception e) {
                        log.error("解析Dify响应失败: {}", data, e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .doOnNext(response -> {
                    log.info("【Dify API】成功处理事件: event={}", response.getEvent());
                })
                .onErrorReturn(createErrorResponse("调用Dify API失败"));
    }
    
    @Override
    public Flux<DifyChatResponse> generateChiefComplaintStream(String transcriptText, String userId, String conversationId) {
        log.info("【Dify API - 病情概要】开始调用，文本长度: {}, 用户ID: {}", transcriptText.length(), userId);
        
        if (this.normalizedApiKey == null || this.normalizedApiKey.isEmpty()) {
            log.error("【Dify API - 病情概要】未配置 API Key");
            return Flux.just(createErrorResponse("Dify API key 未配置"));
        }

        DifyChatRequest request = new DifyChatRequest();
        request.setInputs(new HashMap<>()); 
        request.setQuery("请根据以下语音转录内容生成病情概要：" + transcriptText);
        request.setResponseMode("streaming");
        request.setConversationId(""); 
        request.setUser(userId != null ? userId : "medical_assistant");
        request.setFiles(null);

        return webClient.post()
                .uri("/chat-messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                    response -> {
                        return response.bodyToMono(String.class)
                            .map(errorBody -> {
                                log.error("【Dify API - 病情概要】调用失败: {} - {}", response.statusCode(), errorBody);
                                return new RuntimeException("Dify API调用失败: " + response.statusCode() + " - " + errorBody);
                            });
                    })
                .bodyToFlux(String.class)
                .timeout(java.time.Duration.ofSeconds(30))
                .filter(data -> {
                    String trimmed = data.trim();
                    if (trimmed.isEmpty() || trimmed.equals("[DONE]") || trimmed.equals("data: [DONE]")) {
                        return false;
                    }
                    return trimmed.startsWith("data: ") || trimmed.startsWith("{");
                })
                .map(data -> {
                    try {
                        String jsonStr = data.trim();
                        if (jsonStr.startsWith("data: ")) {
                            jsonStr = jsonStr.substring(6);
                        }
                        
                        DifyChatResponse response = objectMapper.readValue(jsonStr, DifyChatResponse.class);
                        
                        if (response.getAnswer() != null) {
                            String unescapedJson = response.getAnswer()
                                    .replace("&quot;", "\"")
                                    .replace("&amp;", "&")
                                    .replace("&lt;", "<")
                                    .replace("&gt;", ">")
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"");
                            response.setAnswer(unescapedJson);
                        }
                        
                        return response;
                    } catch (Exception e) {
                        log.error("解析Dify响应失败: {}", data, e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .onErrorReturn(createErrorResponse("调用Dify API失败"));
    }
    
    @Override
    public Flux<DifyChatResponse> chatWithDify(String userInput, String userId, String conversationId) {
        log.info("【Dify AI对话】开始调用，用户输入长度: {}, 用户ID: {}", userInput.length(), userId);
        
        if (this.normalizedApiKey == null || this.normalizedApiKey.isEmpty()) {
            log.error("【Dify AI对话】未配置 API Key，停止调用 Dify。");
            return Flux.just(createErrorResponse("Dify API key 未配置"));
        }

        DifyChatRequest request = new DifyChatRequest();
        request.setInputs(new HashMap<>()); 
        request.setQuery("用户咨询：" + userInput);
        request.setResponseMode("streaming");
        request.setConversationId(conversationId != null ? conversationId : ""); 
        request.setUser(userId != null ? userId : "medical_assistant");
        request.setFiles(null);

        try {
            log.info("【Dify AI对话】请求参数: query={}, user={}, responseMode={}", 
                    request.getQuery().substring(0, Math.min(50, request.getQuery().length())) + "...",
                    request.getUser(), request.getResponseMode());
        } catch (Exception e) {
            log.error("序列化请求失败", e);
        }

        return webClient.post()
                .uri("/chat-messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                    response -> {
                        return response.bodyToMono(String.class)
                            .map(errorBody -> {
                                log.error("【Dify AI对话】调用失败: {} - {}", response.statusCode(), errorBody);
                                return new RuntimeException("Dify API调用失败: " + response.statusCode() + " - " + errorBody);
                            });
                    })
                .bodyToFlux(String.class)
                .timeout(java.time.Duration.ofSeconds(60))
                .doOnSubscribe(s -> log.info("【Dify AI对话】开始订阅响应流"))
                .doOnNext(data -> log.info("【Dify AI对话】收到原始数据: {}", data))
                .doOnError(error -> log.error("【Dify AI对话】流处理错误", error))
                .doOnComplete(() -> log.info("【Dify AI对话】流处理完成"))
                .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                .flatMap(chunk -> {
                    // 按\n\n分割数据块
                    String[] blocks = chunk.split("\n\n");
                    return Flux.fromArray(blocks)
                            .filter(block -> block != null && !block.trim().isEmpty());
                })
                .filter(block -> block.startsWith("data: "))
                .map(block -> {
                    try {
                        String jsonStr = block.substring(6).trim(); // 移除 "data: " 前缀
                        log.debug("【Dify AI对话】处理数据块: {}", jsonStr);
                        
                        if (jsonStr.equals("[DONE]")) {
                            // 流结束标记
                            DifyChatResponse endResponse = new DifyChatResponse();
                            endResponse.setEvent("message_end");
                            return endResponse;
                        }
                        
                        DifyChatResponse response = objectMapper.readValue(jsonStr, DifyChatResponse.class);
                        log.info("【Dify AI对话】成功解析响应: event={}, answer存在={}", 
                                response.getEvent(), response.getAnswer() != null);
                        return response;
                    } catch (Exception e) {
                        log.error("解析Dify响应失败: {}", block, e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .onErrorResume(error -> {
                    log.error("【Dify AI对话】调用失败，返回错误响应", error);
                    return Flux.just(createErrorResponse("调用Dify AI对话失败: " + error.getMessage()));
                });
    }
    
    private DifyChatResponse createErrorResponse(String errorMessage) {
        DifyChatResponse errorResponse = new DifyChatResponse();
        errorResponse.setEvent("error");
        errorResponse.setAnswer(errorMessage);
        return errorResponse;
    }

    @Override
    public MedicalSummaryDto extractMedicalSummary(String completeResponse) {
        try {
            log.info("【解析病历总结】开始解析，原始数据: {}", completeResponse.substring(0, Math.min(200, completeResponse.length())));
            
            // 直接解析JSON格式的病历总结
            Map<String, Object> jsonResponse = objectMapper.readValue(completeResponse, Map.class);
            
            // 检查是否有properties字段
            if (jsonResponse.containsKey("properties")) {
                Map<String, Object> properties = (Map<String, Object>) jsonResponse.get("properties");
                return extractFromProperties(properties);
            }
            
            // 如果没有properties，返回默认结构
            MedicalSummaryDto summary = new MedicalSummaryDto();
            summary.setSymptomDetails("症状描述：" + completeResponse);
            summary.setVitalSigns("生命体征：暂无记录");
            summary.setPastMedicalHistory("既往病史：暂无记录");
            summary.setCurrentMedications("当前用药：暂无记录");
            return summary;

        } catch (Exception e) {
            log.error("解析病历总结失败", e);
            MedicalSummaryDto summary = new MedicalSummaryDto();
            summary.setSymptomDetails("症状描述：" + completeResponse);
            summary.setVitalSigns("生命体征：解析失败");
            summary.setPastMedicalHistory("既往病史：解析失败");
            summary.setCurrentMedications("当前用药：解析失败");
            return summary;
        }
    }
    
    private MedicalSummaryDto extractFromProperties(Map<String, Object> properties) {
        MedicalSummaryDto summary = new MedicalSummaryDto();

        // 提取症状详情
        if (properties.containsKey("symptom_details")) {
            Map<String, String> symptomDetails = (Map<String, String>) properties.get("symptom_details");
            String desc = symptomDetails.get("description");
            summary.setSymptomDetails(desc != null && !desc.trim().isEmpty() ? desc : "症状详情：暂无记录");
        }

        // 提取生命体征
        if (properties.containsKey("vital_signs")) {
            Map<String, String> vitalSigns = (Map<String, String>) properties.get("vital_signs");
            String desc = vitalSigns.get("description");
            summary.setVitalSigns(desc != null && !desc.trim().isEmpty() ? desc : "生命体征：暂无记录");
        }

        // 提取既往病史
        if (properties.containsKey("past_medical_history")) {
            Map<String, String> pastHistory = (Map<String, String>) properties.get("past_medical_history");
            String desc = pastHistory.get("description");
            summary.setPastMedicalHistory(desc != null && !desc.trim().isEmpty() ? desc : "既往病史：暂无记录");
        }

        // 提取当前用药
        if (properties.containsKey("current_medications")) {
            Map<String, String> medications = (Map<String, String>) properties.get("current_medications");
            String desc = medications.get("description");
            summary.setCurrentMedications(desc != null && !desc.trim().isEmpty() ? desc : "当前用药：暂无记录");
        }

        return summary;
    }

    private MedicalSummaryDto parseFromPlainText(String text) {
        MedicalSummaryDto summary = new MedicalSummaryDto();

        // 简单的文本解析逻辑，根据关键词提取信息
        String[] sections = text.split("\\n\\n");

        for (String section : sections) {
            if (section.contains("症状") || section.contains("主诉")) {
                summary.setSymptomDetails(section);
            } else if (section.contains("体征") || section.contains("生命")) {
                summary.setVitalSigns(section);
            } else if (section.contains("既往") || section.contains("病史")) {
                summary.setPastMedicalHistory(section);
            } else if (section.contains("用药") || section.contains("药物")) {
                summary.setCurrentMedications(section);
            }
        }

        // 设置默认值
        if (summary.getSymptomDetails() == null) summary.setSymptomDetails("暂无症状详情");
        if (summary.getVitalSigns() == null) summary.setVitalSigns("暂无生命体征数据");
        if (summary.getPastMedicalHistory() == null) summary.setPastMedicalHistory("暂无既往病史");
        if (summary.getCurrentMedications() == null) summary.setCurrentMedications("暂无用药记录");

        return summary;
    }
}
