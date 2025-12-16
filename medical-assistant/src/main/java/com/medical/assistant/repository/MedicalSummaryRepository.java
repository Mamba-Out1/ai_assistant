package com.medical.assistant.repository;

import com.medical.assistant.model.entity.MedicalSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface MedicalSummaryRepository extends JpaRepository<MedicalSummary, Long> {
    Optional<MedicalSummary> findBySummaryId(String summaryId);
    Optional<MedicalSummary> findByVisitId(String visitId);
    List<MedicalSummary> findByDoctorId(String doctorId);
    List<MedicalSummary> findByPatientId(String patientId);
}
