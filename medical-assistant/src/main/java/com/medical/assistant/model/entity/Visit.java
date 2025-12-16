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

    @Column(name = "doctor_id", nullable = false, length = 100)
    private String doctorId;

    @Column(name = "patient_id", nullable = false, length = 100)
    private String patientId;

    @Column(name = "visit_time", nullable = false)
    private LocalDateTime visitTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", columnDefinition = "ENUM('OUTPATIENT', 'INPATIENT', 'FOLLOW_UP', 'EMERGENCY')")
    private VisitType visitType = VisitType.OUTPATIENT;

    @Column(name = "chief_complaint", length = 500)
    private String chiefComplaint;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_status", columnDefinition = "ENUM('IN_PROGRESS', 'COMPLETED', 'CANCELLED')")
    private VisitStatus visitStatus = VisitStatus.IN_PROGRESS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (visitTime == null) {
            visitTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum VisitType {
        OUTPATIENT, INPATIENT, FOLLOW_UP, EMERGENCY
    }

    public enum VisitStatus {
        IN_PROGRESS, COMPLETED, CANCELLED
    }
}
