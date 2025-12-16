package com.medical.assistant.service;

import com.medical.assistant.model.dto.TranscriptionRequest;
import com.medical.assistant.model.dto.TranscriptionResponse;
import com.medical.assistant.model.entity.TranscriptionRecord;

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
     * 根据sessionId查询转写记录
     */
    TranscriptionRecord getTranscriptionBySessionId(String sessionId);

    /**
     * 根据用户ID查询转写记录
     */
    List<TranscriptionRecord> getTranscriptionsByUserId(String userId);

    /**
     * 保存转写记录到数据库
     */
    TranscriptionRecord saveTranscriptionRecord(TranscriptionRecord record);
}