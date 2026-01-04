package com.medical.assistant.controller;

import com.medical.assistant.model.dto.VisitRegistrationRequest;
import com.medical.assistant.model.entity.Visit;
import com.medical.assistant.repository.VisitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

    /**
     * 根据患者ID获取患者信息
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<Visit>> getVisitsByPatientId(@PathVariable String patientId) {
        try {
            List<Visit> visits = visitRepository.findByPatientId(patientId);
            log.info("获取患者ID{}的信息成功，共{}条记录", patientId, visits.size());
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            log.error("获取患者ID{}的信息失败", patientId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据患者姓名获取患者信息
     */
    @GetMapping("/patient-name/{patientName}")
    public ResponseEntity<List<Visit>> getVisitsByPatientName(@PathVariable String patientName) {
        try {
            List<Visit> visits = visitRepository.findByPatientName(patientName);
            log.info("获取患者{}的信息成功，共{}条记录", patientName, visits.size());
            return ResponseEntity.ok(visits);
        } catch (Exception e) {
            log.error("获取患者{}的信息失败", patientName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 患者挂号
     */
    @PostMapping("/register")
    public ResponseEntity<Visit> registerVisit(@RequestBody VisitRegistrationRequest request) {
        try {
            Visit visit = new Visit();
            
            // 使用传入的visit_id
            visit.setVisitId(request.getVisitId());
            
            // 随机分配医生ID (doctor_001~doctor_003)
            String doctorId = "doctor_" + String.format("%03d", new Random().nextInt(3) + 1);
            visit.setDoctorId(doctorId);
            
            // 设置患者信息
            visit.setPatientId(request.getPatientId());
            visit.setPatientName(request.getPatientName());
            visit.setVisitType(request.getVisitType());
            visit.setVisitDate(request.getVisitDate());
            visit.setChiefComplaint(request.getChiefComplaint());
            visit.setNotes(request.getNotes());
            visit.setStatus(Visit.VisitStatus.IN_PROGRESS);
            
            Visit savedVisit = visitRepository.save(visit);
            log.info("患者挂号成功，visit_id: {}, 分配医生: {}", request.getVisitId(), doctorId);
            return ResponseEntity.ok(savedVisit);
        } catch (Exception e) {
            log.error("患者挂号失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    /**
     * 获取下一个visit_id
     */
    @GetMapping("/next-visit-id")
    public ResponseEntity<Map<String, String>> getNextVisitId() {
        try {
            String nextVisitId = generateNextVisitId();
            Map<String, String> result = new HashMap<>();
            result.put("nextVisitId", nextVisitId);
            log.info("生成下一个visit_id成功: {}", nextVisitId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("生成下一个visit_id失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String generateNextVisitId() {
        List<String> visitIds = visitRepository.findAllVisitIdsOrderByDesc();
        if (visitIds.isEmpty()) {
            return "visit_001";
        }

        String latestVisitId = visitIds.get(0);
        int currentNumber = Integer.parseInt(latestVisitId.substring(6));
        return "visit_" + String.format("%03d", currentNumber + 1);
    }}