package com.medical.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class TextToSpeechService {

    /**
     * 处理文本转语音请求
     */
    public String processTextToSpeech(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        log.info("处理TTS请求，文本: {}", text);
        
        // 这里可以集成第三方TTS服务，如百度、阿里云、腾讯云等
        // 目前返回处理成功状态
        return "TTS处理完成: " + text.substring(0, Math.min(text.length(), 50)) + "...";
    }

    /**
     * 生成提示音频
     */
    public byte[] generateNotificationSound() {
        try {
            return generateBeepSound();
        } catch (IOException e) {
            log.error("生成提示音频失败", e);
            return new byte[0];
        }
    }

    /**
     * 生成简单的提示音
     */
    private byte[] generateBeepSound() throws IOException {
        float sampleRate = 8000;
        int duration = 1; // 1秒
        int frequency = 440; // A4音符
        
        int samples = (int) (sampleRate * duration);
        byte[] audioData = new byte[samples * 2]; // 16位音频
        
        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i * frequency / sampleRate;
            short sample = (short) (Math.sin(angle) * 32767 * 0.3); // 30%音量
            
            audioData[i * 2] = (byte) (sample & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        
        return createWavFile(audioData, sampleRate);
    }
    
    /**
     * 创建WAV文件格式
     */
    private byte[] createWavFile(byte[] audioData, float sampleRate) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        // WAV文件头
        out.write("RIFF".getBytes());
        writeInt(out, 36 + audioData.length);
        out.write("WAVE".getBytes());
        out.write("fmt ".getBytes());
        writeInt(out, 16);
        writeShort(out, (short) 1); // PCM
        writeShort(out, (short) 1); // 单声道
        writeInt(out, (int) sampleRate);
        writeInt(out, (int) (sampleRate * 2));
        writeShort(out, (short) 2);
        writeShort(out, (short) 16);
        out.write("data".getBytes());
        writeInt(out, audioData.length);
        out.write(audioData);
        
        return out.toByteArray();
    }
    
    private void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
    
    private void writeShort(ByteArrayOutputStream out, short value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}