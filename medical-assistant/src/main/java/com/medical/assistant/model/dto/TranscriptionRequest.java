package com.medical.assistant.model.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class TranscriptionRequest {

    @NotNull(message = "音频数据不能为空")
    private byte[] audioData;

    private String userId;

    private String audioEncode = "pcm_s16le";

    private Integer sampleRate = 16000;

    private String lang = "autodialect";

    private Integer roleType = 0; // 是否开启说话人分离

    private String pd; // 领域个性化参数

    private Integer engPunc = 1; // 是否返回标点

    private Integer engVadMdn = 1; // vad远近场切换

    private String visitId;
}