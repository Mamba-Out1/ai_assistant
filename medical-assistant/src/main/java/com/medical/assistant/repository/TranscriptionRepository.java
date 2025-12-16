package com.medical.assistant.repository;

import com.medical.assistant.model.entity.TranscriptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TranscriptionRepository extends JpaRepository<TranscriptionRecord, Long> {

    Optional<TranscriptionRecord> findBySessionId(String sessionId);

    List<TranscriptionRecord> findByUserId(String userId);

    List<TranscriptionRecord> findByUserIdAndCreatedAtBetween(
            String userId, LocalDateTime startTime, LocalDateTime endTime);

    List<TranscriptionRecord> findByStatus(String status);
}