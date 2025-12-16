package com.medical.assistant.repository;

import com.medical.assistant.model.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TranscriptionRepository extends JpaRepository<Transcript, Long> {

    Optional<Transcript> findBySessionId(String sessionId);

    List<Transcript> findByUserId(String userId);

    List<Transcript> findByUserIdAndCreatedAtBetween(
            String userId, LocalDateTime startTime, LocalDateTime endTime);

    List<Transcript> findByStatus(String status);
}