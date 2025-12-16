package com.medical.assistant.repository;

import com.medical.assistant.model.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TranscriptRepository extends JpaRepository<Transcript, Long> {
    Optional<Transcript> findByTranscriptId(String transcriptId);
    Optional<Transcript> findByVisitId(String visitId);
}
