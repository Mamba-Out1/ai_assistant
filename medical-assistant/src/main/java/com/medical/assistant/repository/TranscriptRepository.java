package com.medical.assistant.repository;

import com.medical.assistant.model.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TranscriptRepository extends JpaRepository<Transcript, Long> {
    Optional<Transcript> findByTranscriptId(String transcriptId);
    Optional<Transcript> findByVisitId(String visitId);
    
    @Query("SELECT t FROM Transcript t WHERE t.visitId = :visitId ORDER BY t.createdAt DESC LIMIT 1")
    Optional<Transcript> findLatestByVisitId(@Param("visitId") String visitId);
}
