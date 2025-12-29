package com.medical.assistant.service;

import com.medical.assistant.model.dto.TranscriptionRequest;
import com.medical.assistant.model.dto.TranscriptionResponse;
import com.medical.assistant.model.dto.MedicalSummaryDto;
import com.medical.assistant.model.entity.Transcript;
import reactor.core.publisher.Flux;

import java.util.List;

public interface TranscriptionService {

    /**
     * 实时语音转写
     */
    TranscriptionResponse transcribeAudio(TranscriptionRequest request) throws Exception;

    /**
     * 流式语音转写（分块发送）
     */
    String startStreamTranscription(TranscriptionRequest request) throws Exception;

    /**
     * 发送音频流数据
     */
    void sendAudioStream(String sessionId, byte[] audioData) throws Exception;

    /**
     * 结束流式转写
     */
    TranscriptionResponse endStreamTranscription(String sessionId) throws Exception;

    /**
     * 根据sessionId查询转写记录（可能需要调整）
     */
    Transcript getTranscriptionBySessionId(String sessionId);

    /**
     * 根据用户ID查询转写记录
     */
    List<Transcript> getTranscriptionsByUserId(String userId);

    /**
     * 保存转写记录
     */
    Transcript saveTranscriptionRecord(Transcript record);

    /**
     * 保存转录结果到transcripts表
     */
    Transcript saveTranscript(String visitId, String transcriptText, Integer audioDuration, String audioFormat);

    /**
     * 调用智能体生成病历总结（流式）
     */
    Flux<String> generateMedicalSummaryStream(String visitId, String transcriptText, String doctorId, String patientId);

    /**
     * 调用智能体生成病情概要（流式）
     */
    Flux<String> generateChiefComplaintStream(String visitId, String transcriptText, String doctorId, String patientId);

    /**
     * 保存病历总结
     */
    void saveMedicalSummary(MedicalSummaryDto summaryDto);

    /**
     * Dify AI对话（流式）
     */
    Flux<String> chatWithDifyStream(String query, String userId);
}
