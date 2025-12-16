package com.medical.assistant.model.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@Data
public class DifyChatRequest {
    private String query;
    private Map<String, Object> inputs;

    @JsonProperty("response_mode")
    private String responseMode = "streaming";

    private String user;

    @JsonProperty("conversation_id")
    private String conversationId;

    private List<FileObject> files;

    @JsonProperty("auto_generate_name")
    private Boolean autoGenerateName = true;

    @JsonProperty("workflow_id")
    private String workflowId;

    @JsonProperty("trace_id")
    private String traceId;

    @Data
    public static class FileObject {
        private String type;

        @JsonProperty("transfer_method")
        private String transferMethod;

        private String url;

        @JsonProperty("upload_file_id")
        private String uploadFileId;
    }
}
