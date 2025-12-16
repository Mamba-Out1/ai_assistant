package com.medical.assistant.websocket;

public interface XunfeiWebSocketHandler {

    /**
     * 连接成功
     */
    void onConnected();

    /**
     * 握手成功
     */
    void onHandshakeSuccess(String sessionId);

    /**
     * 收到转写结果
     * @param text 转写文本
     * @param isFinal 是否为确定性结果
     */
    void onTranscriptionResult(String text, boolean isFinal);

    /**
     * 转写完成
     */
    void onTranscriptionComplete(String fullText);

    /**
     * 发生错误
     */
    void onError(Exception e);

    /**
     * 连接关闭
     */
    void onClosed(int code, String reason);
}