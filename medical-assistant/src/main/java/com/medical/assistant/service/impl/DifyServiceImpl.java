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

    @Autowired
    private DifyConfig difyConfig;

    public DifyServiceImpl(DifyConfig difyConfig, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(difyConfig.getApiUrl())
                .defaultHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        
        log.info("【Dify配置】API URL: {}, API Key: {}***", 
                difyConfig.getApiUrl(), 
                difyConfig.getApiKey().substring(0, Math.min(10, difyConfig.getApiKey().length())));
    }

    @Override
    public Flux<DifyChatResponse> generateMedicalSummaryStream(String transcriptText, String userId, String conversationId) {
        log.info("【Dify API】开始调用，文本长度: {}, 用户ID: {}", transcriptText.length(), userId);
        
        // 构建请求（严格按照Dify API文档格式）
        DifyChatRequest request = new DifyChatRequest();
        request.setInputs(new HashMap<>()); // 空对象
        request.setQuery("请根据以下语音转录内容生成病历总结：" + transcriptText);
        request.setResponseMode("streaming");
        request.setConversationId(""); // 空字符串而不是null
        request.setUser(userId != null ? userId : "medical_assistant"); // 确保不为null

        log.info("【Dify API】请求参数: {}", request);

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
                .doOnNext(data -> log.debug("【Dify API】收到数据: {}", data))
                .filter(data -> data.startsWith("data: "))
                .map(data -> {
                    try {
                        String jsonStr = data.substring(6); // 移除 "data: " 前缀
                        return objectMapper.readValue(jsonStr, DifyChatResponse.class);
                    } catch (Exception e) {
                        log.error("解析Dify响应失败: {}", data, e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .onErrorReturn(createErrorResponse("调用Dify API失败"));
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
            // 尝试解析structured_output
            Map<String, Object> response = objectMapper.readValue(completeResponse, Map.class);

            if (response.containsKey("structured_output")) {
                Map<String, Object> structuredOutput = (Map<String, Object>) response.get("structured_output");
                Map<String, Object> properties = (Map<String, Object>) structuredOutput.get("properties");

                MedicalSummaryDto summary = new MedicalSummaryDto();

                // 提取症状详情
                if (properties.containsKey("symptom_details")) {
                    Map<String, String> symptomDetails = (Map<String, String>) properties.get("symptom_details");
                    summary.setSymptomDetails(symptomDetails.get("description"));
                }

                // 提取生命体征
                if (properties.containsKey("vital_signs")) {
                    Map<String, String> vitalSigns = (Map<String, String>) properties.get("vital_signs");
                    summary.setVitalSigns(vitalSigns.get("description"));
                }

                // 提取既往病史
                if (properties.containsKey("past_medical_history")) {
                    Map<String, String> pastHistory = (Map<String, String>) properties.get("past_medical_history");
                    summary.setPastMedicalHistory(pastHistory.get("description"));
                }

                // 提取当前用药
                if (properties.containsKey("current_medications")) {
                    Map<String, String> medications = (Map<String, String>) properties.get("current_medications");
                    summary.setCurrentMedications(medications.get("description"));
                }

                return summary;
            }

            // 如果没有structured_output，尝试从普通文本中解析
            return parseFromPlainText(completeResponse);

        } catch (Exception e) {
            log.error("解析病历总结失败", e);
            // 返回包含原始文本的默认结构
            MedicalSummaryDto summary = new MedicalSummaryDto();
            summary.setSymptomDetails("症状描述：" + completeResponse);
            summary.setVitalSigns("生命体征：暂无记录");
            summary.setPastMedicalHistory("既往病史：暂无记录");
            summary.setCurrentMedications("当前用药：暂无记录");
            return summary;
        }
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
