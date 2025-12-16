package com.medical.assistant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionResponse {

    private String sessionId;

    private String transcriptionText;

    private String status;

    private String message;

    private Long recordId;

    public static TranscriptionResponse success(String sessionId, String text, Long recordId) {
        return TranscriptionResponse.builder()
                .sessionId(sessionId)
                .transcriptionText(text)
                .status("SUCCESS")
                .recordId(recordId)
                .build();
    }

    public static TranscriptionResponse error(String sessionId, String message) {
        return TranscriptionResponse.builder()
                .sessionId(sessionId)
                .status("ERROR")
                .message(message)
                .build();
    }
}