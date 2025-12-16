package com.medical.assistant.model.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transcription_records")
public class TranscriptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "transcription_text", columnDefinition = "TEXT")
    private String transcriptionText;

    @Column(name = "audio_duration")
    private Integer audioDuration;

    @Column(name = "audio_format", length = 50)
    private String audioFormat;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    @Column(name = "language", length = 50)
    private String language = "zh-CN";  // 添加默认值

    @Column(name = "status", length = 20)
    private String status = "PROCESSING";  // 添加默认值

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;

        // 确保必要字段有默认值
        if (this.language == null) {
            this.language = "zh-CN";
        }
        if (this.status == null) {
            this.status = "PROCESSING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 添加便捷方法
    public void markAsCompleted() {
        this.status = "COMPLETED";
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsFailed(String error) {
        this.status = "FAILED";
        this.errorMessage = error;
        this.updatedAt = LocalDateTime.now();
    }
}