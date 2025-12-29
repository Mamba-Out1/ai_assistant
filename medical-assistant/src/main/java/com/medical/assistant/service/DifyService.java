package com.medical.assistant.service;

import com.medical.assistant.model.dto.DifyChatRequest;
import com.medical.assistant.model.dto.DifyChatResponse;
import com.medical.assistant.model.dto.MedicalSummaryDto;
import reactor.core.publisher.Flux;

public interface DifyService {
    /**
     * 流式调用Dify智能体生成病历总结
     */
    Flux<DifyChatResponse> generateMedicalSummaryStream(String transcriptText, String userId, String conversationId);

    /**
     * 从流式响应中提取病历总结
     */
    MedicalSummaryDto extractMedicalSummary(String completeResponse);
    
    /**
     * Dify AI对话
     */
    Flux<DifyChatResponse> chatWithDify(String userInput, String userId, String conversationId);
}
