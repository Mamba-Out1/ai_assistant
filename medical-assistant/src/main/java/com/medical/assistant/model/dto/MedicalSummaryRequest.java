package com.medical.assistant.model.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class MedicalSummaryRequest {
    @NotBlank(message = "visitId不能为空")
    private String visitId;

    @NotBlank(message = "doctorId不能为空")
    private String doctorId;

    @NotBlank(message = "patientId不能为空")
    private String patientId;
}
