package com.medical.assistant.repository;

import com.medical.assistant.model.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {
    Optional<Visit> findByVisitId(String visitId);
    List<Visit> findByDoctorId(String doctorId);
    List<Visit> findByPatientId(String patientId);
    List<Visit> findByPatientName(String patientName);
    List<Visit> findByPatientNameContaining(String patientName);
    List<Visit> findByDoctorIdAndPatientId(String doctorId, String patientId);
    
    @Query("SELECT v.visitId FROM Visit v ORDER BY v.visitId DESC")
    List<String> findAllVisitIdsOrderByDesc();
}
