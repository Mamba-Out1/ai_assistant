package com.medical.assistant.controller;

import com.medical.assistant.model.entity.Visit;
import com.medical.assistant.repository.VisitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/visits")
@CrossOrigin(origins = "*")
public class VisitController {

    @Autowired
    private VisitRepository visitRepository;

    /**
     * 获取所有患者信息
     */
    @GetMapping("/all")
    public ResponseEntity<List<Visit>> getAllVisits() {
        try {
            List<Visit> visits = visitRepository.findAll();
            log.info("获取所有患者信息成功，共{}条记录", visits.size());
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            log.error("获取所有患者信息失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据医生ID获取患者信息
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<Visit>> getVisitsByDoctor(@PathVariable String doctorId) {
        try {
            List<Visit> visits = visitRepository.findByDoctorId(doctorId);
            log.info("获取医生{}的患者信息成功，共{}条记录", doctorId, visits.size());
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            log.error("获取医生{}的患者信息失败", doctorId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}