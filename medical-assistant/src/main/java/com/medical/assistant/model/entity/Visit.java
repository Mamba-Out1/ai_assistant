package com.medical.assistant.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "visits")
public class Visit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_id", nullable = false, unique = true, length = 100)
    private String visitId;

    @Column(name = "patient_id", nullable = false, length = 100)
    private String patientId;

    @Column(name = "patient_name", length = 100)
    private String patientName;

    @Column(name = "doctor_id", length = 100)
    private String doctorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type")
    private VisitType visitType = VisitType.CONSULTATION;

    @Column(name = "visit_date")
    private LocalDateTime visitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private VisitStatus status = VisitStatus.SCHEDULED;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (visitDate == null) {
            visitDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum VisitType {
        CONSULTATION, FOLLOW_UP, EMERGENCY, ROUTINE_CHECK
    }

    public enum VisitStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
