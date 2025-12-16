package com.medical.assistant.model.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@Data
public class DifyChatResponse {
    private String event;

    @JsonProperty("task_id")
    private String taskId;

    private String id;

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("conversation_id")
    private String conversationId;

    private String mode;
    private String answer;
    private Map<String, Object> metadata;
    private Usage usage;

    @JsonProperty("retriever_resources")
    private List<RetrieverResource> retrieverResources;

    @JsonProperty("created_at")
    private Long createdAt;

    // 流式特有字段
    private String audio;

    // workflow相关
    @JsonProperty("workflow_run_id")
    private String workflowRunId;

    private Map<String, Object> data;

    // 错误相关
    private Integer status;
    private String code;
    private String message;

    @Data
    public static class Usage {
        private Integer tokens;
        private String currency;

        @JsonProperty("total_price")
        private Double totalPrice;
    }

    @Data
    public static class RetrieverResource {
        private String position;
        private String content;
        private String source;
    }
}
