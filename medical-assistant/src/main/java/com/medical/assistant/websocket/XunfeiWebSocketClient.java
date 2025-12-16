package com.medical.assistant.websocket;

import com.google.gson.Gson;
import com.medical.assistant.model.dto.XunfeiResponse;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class XunfeiWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(XunfeiWebSocketClient.class);
    private final Gson gson = new Gson();
    private final XunfeiWebSocketHandler handler;
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    private StringBuilder fullTranscription = new StringBuilder();
    private String sessionId;

    public XunfeiWebSocketClient(URI serverUri, XunfeiWebSocketHandler handler) {
        super(serverUri);
        this.handler = handler;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket连接已建立");
        connectLatch.countDown();
        handler.onConnected();
    }

    @Override
    public void onMessage(String message) {
        logger.debug("收到消息: {}", message);

        try {
            XunfeiResponse response = gson.fromJson(message, XunfeiResponse.class);

            // 保存sessionId
            if (response.getSid() != null) {
                this.sessionId = response.getSid();
            }

            if ("started".equals(response.getAction())) {
                logger.info("握手成功，session ID: {}", response.getSid());
                handler.onHandshakeSuccess(response.getSid());

            } else if ("result".equals(response.getAction())) {
                // 解析转写结果
                String text = parseTranscriptionResult(response);
                if (text != null && !text.isEmpty()) {
                    fullTranscription.append(text);
                    handler.onTranscriptionResult(text, response.getData().getCn().getSt().getType() == 0);
                }

                // 检查是否为最终结果
                if (Boolean.TRUE.equals(response.getData().getLs())) {
                    logger.info("收到最终结果");
                    handler.onTranscriptionComplete(fullTranscription.toString());
                }

            } else if ("error".equals(response.getAction())) {
                logger.error("讯飞返回错误: code={}, desc={}", response.getCode(), response.getDesc());
                handler.onError(new Exception("讯飞错误: " + response.getDesc()));
            }

        } catch (Exception e) {
            logger.error("解析消息失败", e);
            handler.onError(e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket连接已关闭: code={}, reason={}, remote={}", code, reason, remote);
        closeLatch.countDown();
        handler.onClosed(code, reason);
    }

    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket发生错误", ex);
        handler.onError(ex);
    }

    /**
     * 发送音频数据
     */
    public void sendAudio(byte[] audioData) {
        if (isOpen()) {
            send(audioData);
            logger.debug("发送音频数据: {} bytes", audioData.length);
        } else {
            logger.warn("WebSocket未连接，无法发送音频数据");
        }
    }

    /**
     * 发送音频数据结束标识
     */
    public void sendEndFlag() {
        if (isOpen()) {
            // 发送空数据表示结束
            send(new byte[0]);
            logger.info("发送音频结束标识");
        }
    }

    /**
     * 等待连接建立
     */
    public void awaitConnection() throws InterruptedException {
        connectLatch.await();
    }

    /**
     * 等待连接关闭
     */
    public boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException {
        return closeLatch.await(timeout, unit);
    }

    /**
     * 解析转写结果
     */
    private String parseTranscriptionResult(XunfeiResponse response) {
        if (response.getData() == null || response.getData().getCn() == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        XunfeiResponse.DataBean.CnBean.StBean st = response.getData().getCn().getSt();

        if (st != null && st.getRt() != null) {
            for (XunfeiResponse.DataBean.CnBean.StBean.RtBean rt : st.getRt()) {
                if (rt.getWs() != null) {
                    for (XunfeiResponse.DataBean.CnBean.StBean.RtBean.WsBean ws : rt.getWs()) {
                        if (ws.getCw() != null) {
                            for (XunfeiResponse.DataBean.CnBean.StBean.RtBean.WsBean.CwBean cw : ws.getCw()) {
                                result.append(cw.getW());
                            }
                        }
                    }
                }
            }
        }

        return result.toString();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFullTranscription() {
        return fullTranscription.toString();
    }
}