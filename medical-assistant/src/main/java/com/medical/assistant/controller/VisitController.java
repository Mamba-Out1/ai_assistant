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
    @PostMapping("/register/{patientId}/{patientName}/{visitDate}")
    public ResponseEntity<Visit> registerVisit(
            @PathVariable String patientId,
            @PathVariable String patientName,
            @PathVariable String visitDate) {
        try {
            // 获取数据库中最新的visitId
            List<String> visitIds = visitRepository.findAllVisitIdsOrderByDesc();
            if (visitIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            String latestVisitId = visitIds.get(0);
            
            // 查找对应的Visit记录
            Visit visit = visitRepository.findByVisitId(latestVisitId).orElse(null);
            if (visit == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 随机分配医生ID (doctor_001~doctor_003)
            String doctorId = "doctor_" + String.format("%03d", new Random().nextInt(3) + 1);
            
            // 更新患者信息
            visit.setPatientId(patientId);
            visit.setPatientName(patientName);
            visit.setDoctorId(doctorId);
            visit.setVisitDate(java.time.LocalDate.parse(visitDate).atStartOfDay());
            visit.setStatus(Visit.VisitStatus.IN_PROGRESS);
            
            Visit savedVisit = visitRepository.save(visit);
            log.info("患者挂号成功，visit_id: {}, 患者ID: {}, 患者姓名: {}, 就诊时间: {}, 分配医生: {}", 
                latestVisitId, patientId, patientName, visitDate, doctorId);
            return ResponseEntity.ok(savedVisit);
        } catch (Exception e) {
            log.error("患者挂号失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    /**
     * 获取下一个visit_id
     */
    @GetMapping("/next-visit-id/{patientId}")
    public ResponseEntity<Map<String, String>> getNextVisitId(@PathVariable String patientId) {
        try {
            String visitId = getOrCreateNextVisitId(patientId);
            Map<String, String> result = new HashMap<>();
            result.put("nextVisitId", visitId);
            log.info("获取visit_id成功: {}, 患者ID: {}", visitId, patientId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取visit_id失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String getOrCreateNextVisitId(String patientId) {
        List<String> visitIds = visitRepository.findAllVisitIdsOrderByDesc();
        
        if (!visitIds.isEmpty()) {
            String latestVisitId = visitIds.get(0);
            // 检查最新visitId对应的记录的chiefComplaint和notes是否为空
            Visit latestVisit = visitRepository.findByVisitId(latestVisitId).orElse(null);
            if (latestVisit != null && 
                (latestVisit.getChiefComplaint() == null || latestVisit.getChiefComplaint().trim().isEmpty()) &&
                (latestVisit.getNotes() == null || latestVisit.getNotes().trim().isEmpty())) {
                // 如果为空，使用这个visitId并更新患者ID
                latestVisit.setPatientId(patientId);
                visitRepository.save(latestVisit);
                return latestVisitId;
            }
        }
        
        // 生成下一个visitId并存入数据库
        String nextVisitId;
        if (visitIds.isEmpty()) {
            nextVisitId = "visit_001";
        } else {
            String latestVisitId = visitIds.get(0);
            int currentNumber = Integer.parseInt(latestVisitId.substring(6));
            nextVisitId = "visit_" + String.format("%03d", currentNumber + 1);
        }
        
        // 将新visitId存入visits表，设置患者ID
        Visit newVisit = new Visit();
        newVisit.setVisitId(nextVisitId);
        newVisit.setPatientId(patientId);
        newVisit.setChiefComplaint(null);
        newVisit.setNotes(null);
        visitRepository.save(newVisit);
        
        return nextVisitId;
    }}