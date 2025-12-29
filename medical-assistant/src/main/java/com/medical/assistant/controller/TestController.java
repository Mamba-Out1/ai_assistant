package com.medical.assistant.controller;

import com.medical.assistant.model.entity.MedicalSummary;
import com.medical.assistant.model.entity.Transcript;
import com.medical.assistant.repository.MedicalSummaryRepository;
import com.medical.assistant.repository.TranscriptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    @Autowired
    private MedicalSummaryRepository medicalSummaryRepository;

    @Autowired
    private TranscriptRepository transcriptRepository;

    /**
     * 创建测试病历总结数据
     */
    @PostMapping("/create-test-summary")
    public ResponseEntity<Map<String, Object>> createTestSummary(@RequestParam String visitId) {
        try {
            // 检查是否已存在
            if (medicalSummaryRepository.findByVisitId(visitId).isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "EXISTS");
                response.put("message", "病历总结已存在");
                return ResponseEntity.ok(response);
            }

            MedicalSummary summary = new MedicalSummary();
            summary.setSummaryId(UUID.randomUUID().toString());
            summary.setVisitId(visitId);
            summary.setDoctorId("test_doctor_001");
            summary.setPatientId("test_patient_001");
            summary.setSymptomDetails("测试症状详情：患者主诉咽痛，始于前天晚上，伴发热38.5℃，全身乏力，干咳，鼻塞流涕。");
            summary.setVitalSigns("测试生命体征：体温38.5℃，咽部充血明显，扁桃体肿大，心肺听诊正常。");
            summary.setPastMedicalHistory("测试既往病史：平素体健，有过敏性鼻炎病史（秋季易发作），无药物过敏史。");
            summary.setCurrentMedications("测试当前用药：昨日自行服用布洛芬一片退热，体温可暂时下降但会再次升高。");

            MedicalSummary saved = medicalSummaryRepository.save(summary);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "测试病历总结创建成功");
            response.put("summaryId", saved.getSummaryId());
            response.put("visitId", saved.getVisitId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("创建测试病历总结失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 创建测试转录数据
     */
    @PostMapping("/create-test-transcript")
    public ResponseEntity<Map<String, Object>> createTestTranscript(@RequestParam String visitId) {
        try {
            // 检查是否已存在
            if (transcriptRepository.findByVisitId(visitId).isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "EXISTS");
                response.put("message", "转录记录已存在");
                return ResponseEntity.ok(response);
            }

            Transcript transcript = new Transcript();
            transcript.setTranscriptId(UUID.randomUUID().toString());
            transcript.setVisitId(visitId);
            transcript.setTranscriptText("医生：您好，请问您哪里不舒服？患者：医生您好，我从前天晚上开始咽痛，最开始是咽干，昨天加重了。还有发热，昨天自己测量最高38度5，今天早上37度8。还感觉全身乏力。医生：还有其他症状吗？患者：有干咳，痰不多，还有鼻塞流清鼻涕，头痛，全身关节酸痛。因为咽痛吞咽困难，食欲也不好，鼻塞影响睡眠，大小便正常。医生：以前有什么病史吗？患者：平时身体还可以，有过敏性鼻炎，秋天容易发作。没有药物过敏史。医生：用过什么药吗？患者：昨天吃了一片布洛芬退热，体温能暂时下降，但过一会又会升高。");
            transcript.setAudioDuration(120);
            transcript.setAudioFormat("pcm_s16le");
            transcript.setStatus(Transcript.TranscriptStatus.COMPLETED);

            Transcript saved = transcriptRepository.save(transcript);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "测试转录记录创建成功");
            response.put("transcriptId", saved.getTranscriptId());
            response.put("visitId", saved.getVisitId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("创建测试转录记录失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 获取所有病历总结（调试用）
     */
    @GetMapping("/summaries")
    public ResponseEntity<List<MedicalSummary>> getAllSummaries() {
        try {
            List<MedicalSummary> summaries = medicalSummaryRepository.findAll();
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("获取所有病历总结失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取所有转录记录（调试用）
     */
    @GetMapping("/transcripts")
    public ResponseEntity<List<Transcript>> getAllTranscripts() {
        try {
            List<Transcript> transcripts = transcriptRepository.findAll();
            return ResponseEntity.ok(transcripts);
        } catch (Exception e) {
            log.error("获取所有转录记录失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 清理测试数据
     */
    @DeleteMapping("/cleanup/{visitId}")
    public ResponseEntity<Map<String, String>> cleanupTestData(@PathVariable String visitId) {
        try {
            // 删除病历总结
            medicalSummaryRepository.findByVisitId(visitId).ifPresent(summary -> {
                medicalSummaryRepository.delete(summary);
                log.info("删除病历总结: {}", summary.getSummaryId());
            });

            // 删除转录记录
            transcriptRepository.findByVisitId(visitId).ifPresent(transcript -> {
                transcriptRepository.delete(transcript);
                log.info("删除转录记录: {}", transcript.getTranscriptId());
            });

            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "测试数据清理完成");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("清理测试数据失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}