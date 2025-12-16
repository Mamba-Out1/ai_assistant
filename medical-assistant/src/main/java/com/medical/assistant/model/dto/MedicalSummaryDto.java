package com.medical.assistant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalSummaryDto {
    private String summaryId;
    private String visitId;
    private String doctorId;
    private String patientId;
    private String symptomDetails;
    private String vitalSigns;
    private String pastMedicalHistory;
    private String currentMedications;
    private String rawResponse; // 保存原始响应
}
