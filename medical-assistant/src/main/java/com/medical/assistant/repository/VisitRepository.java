package com.medical.assistant.repository;

import com.medical.assistant.model.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {
    Optional<Visit> findByVisitId(String visitId);
    List<Visit> findByDoctorId(String doctorId);
    List<Visit> findByPatientId(String patientId);
    List<Visit> findByDoctorIdAndPatientId(String doctorId, String patientId);
}
