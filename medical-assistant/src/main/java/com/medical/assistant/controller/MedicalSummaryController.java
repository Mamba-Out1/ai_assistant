package com.medical.assistant.controller;

import com.medical.assistant.model.dto.MedicalSummaryRequest;
import com.medical.assistant.model.dto.MedicalSummaryResponse;
import com.medical.assistant.model.entity.MedicalSummary;
import com.medical.assistant.model.entity.Transcript;
import com.medical.assistant.service.TranscriptionService;
import com.medical.assistant.repository.MedicalSummaryRepository;
import com.medical.assistant.repository.TranscriptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/medical-summary")
@CrossOrigin(origins = "*")
public class MedicalSummaryController {

    @Autowired
    private TranscriptionService transcriptionService;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private MedicalSummaryRepository medicalSummaryRepository;

    /**
     * 根据visitId生成病历总结（流式返回）
     */
    @PostMapping(value = "/generate/{visitId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateMedicalSummary(
            @PathVariable String visitId,
            @RequestParam String doctorId,
            @RequestParam String patientId) {

        log.info("【病历总结】开始生成，visitId: {}, doctorId: {}, patientId: {}",
                visitId, doctorId, patientId);

        try {
            // 获取转录文本
            Transcript transcript = transcriptRepository.findByVisitId(visitId)
                    .orElseThrow(() -> new RuntimeException("未找到visitId=" + visitId + "的转录记录"));

            log.info("【病历总结】找到转录记录，文本长度: {}", 
                    transcript.getTranscriptText() != null ? transcript.getTranscriptText().length() : 0);

            if (transcript.getTranscriptText() == null || transcript.getTranscriptText().isEmpty()) {
                return Flux.just("data: {\"event\": \"error\", \"message\": \"转录文本为空\"}\n\n");
            }
        } catch (Exception e) {
            log.error("【病历总结】获取转录记录失败", e);
            return Flux.just("data: {\"event\": \"error\", \"message\": \"" + e.getMessage() + "\"}\n\n");
        }

        try {
            // 获取转录文本
            Transcript transcript = transcriptRepository.findByVisitId(visitId).get();
            
            // 调用智能体生成病历总结
            return transcriptionService.generateMedicalSummaryStream(
                            visitId,
                            transcript.getTranscriptText(),
                            doctorId,
                            patientId
                    )
                    .map(content -> {
                        if (content.equals("[COMPLETED]")) {
                            return "data: {\"event\": \"completed\", \"message\": \"病历总结生成完成\"}\n\n";
                        } else if (content.startsWith("[ERROR]")) {
                            return "data: {\"event\": \"error\", \"message\": \"" + content.substring(7) + "\"}\n\n";
                        } else {
                            return "data: {\"event\": \"message\", \"content\": \"" +
                                    content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}\n\n";
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("【Dify API】调用失败，使用备用方案", error);
                        return generateSimpleMedicalSummary(transcript.getTranscriptText())
                                .map(content -> {
                                    if (content.equals("[COMPLETED]")) {
                                        return "data: {\"event\": \"completed\", \"message\": \"病历总结生成完成\"}";


                                    } else if (content.startsWith("[ERROR]")) {
                                        return "data: {\"event\": \"error\", \"message\": \"" + content.substring(7) + "\"}";


                                    } else {
                                        return content; // 已经是SSE格式
                                    }
                                });
                    });
        } catch (Exception e) {
            log.error("【病历总结】生成失败", e);
            return Flux.just("data: {\"event\": \"error\", \"message\": \"" + e.getMessage() + "\"}\n\n");
        }
    }

    /**
     * 备用方案：生成简单的病历总结
     */
    private Flux<String> generateSimpleMedicalSummary(String transcriptText) {
        return Flux.create(sink -> {
            try {
                sink.next("data: {\"event\": \"message\", \"content\": \"正在使用备用方案生成病历总结...\"}\n\n");
                Thread.sleep(500);
                
                String summary = "病历总结\n==========\n主诉症状：根据语音记录分析\n原始记录：" + transcriptText + "\n\n建议：进一步检查和详细问诊";
                String[] lines = summary.split("\n");
                
                for (String line : lines) {
                    sink.next("data: {\"event\": \"message\", \"content\": \"" + line.replace("\"", "\\\"") + "\n\"}\n\n");
                    Thread.sleep(100);
                }
                
                sink.next("data: {\"event\": \"completed\", \"message\": \"病历总结生成完成\"}\n\n");
                sink.complete();
                
            } catch (Exception e) {
                sink.next("data: {\"event\": \"error\", \"message\": \"" + e.getMessage() + "\"}\n\n");
                sink.complete();
            }
        });
    }

    /**
     * 获取病历总结
     */
    @GetMapping("/visit/{visitId}")
    public ResponseEntity<MedicalSummaryResponse> getMedicalSummaryByVisit(@PathVariable String visitId) {
        try {
            MedicalSummary summary = medicalSummaryRepository.findByVisitId(visitId)
                    .orElseThrow(() -> new RuntimeException("未找到病历总结"));

            MedicalSummaryResponse response = MedicalSummaryResponse.builder()
                    .summaryId(summary.getSummaryId())
                    .visitId(summary.getVisitId())
                    .doctorId(summary.getDoctorId())
                    .patientId(summary.getPatientId())
                    .symptomDetails(summary.getSymptomDetails())
                    .vitalSigns(summary.getVitalSigns())
                    .pastMedicalHistory(summary.getPastMedicalHistory())
                    .currentMedications(summary.getCurrentMedications())
                    .createdAt(summary.getCreatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取病历总结失败", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 手动创建病历总结
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createMedicalSummary(@Valid @RequestBody MedicalSummaryRequest request) {
        try {
            // 获取转录文本
            Transcript transcript = transcriptRepository.findByVisitId(request.getVisitId())
                    .orElseThrow(() -> new RuntimeException("未找到转录记录"));

            // 同步调用生成病历总结
            transcriptionService.generateMedicalSummaryStream(
                    request.getVisitId(),
                    transcript.getTranscriptText(),
                    request.getDoctorId(),
                    request.getPatientId()
            ).blockLast(); // 等待完成

            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "病历总结创建成功");
            response.put("visitId", request.getVisitId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("创建病历总结失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
