package com.medical.assistant.model.dto;

import lombok.Data;

@Data
public class DoctorDto {
    private Long id;
    private String doctorId;
    private String name;
    private String specialization;
    private String phone;
    private String email;
    private String status;
}