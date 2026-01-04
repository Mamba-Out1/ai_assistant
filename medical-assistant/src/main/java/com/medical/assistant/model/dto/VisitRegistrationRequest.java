package com.medical.assistant.model.dto;

import com.medical.assistant.model.entity.Visit;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VisitRegistrationRequest {
    private String visitId;
    private String patientId;
    private String patientName;
    private Visit.VisitType visitType;
    private LocalDateTime visitDate;
    private String chiefComplaint;
    private String notes;
}