package com.medical.assistant.model.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transcripts")
public class Transcript {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transcript_id", nullable = false, unique = true, length = 100)
    private String transcriptId;

    @Column(name = "visit_id", nullable = false, length = 100)
    private String visitId;

    @Column(name = "transcript_text", columnDefinition = "LONGTEXT")
    private String transcriptText;

    @Column(name = "audio_duration")
    private Integer audioDuration;

    @Column(name = "audio_format", length = 50)
    private String audioFormat;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PROCESSING', 'COMPLETED', 'FAILED')")
    private TranscriptStatus status = TranscriptStatus.PROCESSING;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

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

    public enum TranscriptStatus {
        PROCESSING, COMPLETED, FAILED
    }
}
