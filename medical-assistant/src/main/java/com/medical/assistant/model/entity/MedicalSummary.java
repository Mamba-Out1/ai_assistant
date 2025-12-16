package com.medical.assistant.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "medical_summaries")
public class MedicalSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary_id", nullable = false, unique = true, length = 100)
    private String summaryId;

    @Column(name = "visit_id", nullable = false, length = 100)
    private String visitId;

    @Column(name = "doctor_id", nullable = false, length = 100)
    private String doctorId;

    @Column(name = "patient_id", nullable = false, length = 100)
    private String patientId;

    @Column(name = "symptom_details", nullable = false, columnDefinition = "TEXT")
    private String symptomDetails;

    @Column(name = "vital_signs", nullable = false, columnDefinition = "TEXT")
    private String vitalSigns;

    @Column(name = "past_medical_history", nullable = false, columnDefinition = "TEXT")
    private String pastMedicalHistory;

    @Column(name = "current_medications", nullable = false, columnDefinition = "TEXT")
    private String currentMedications;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
