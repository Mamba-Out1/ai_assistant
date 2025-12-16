package com.medical.assistant.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.medical.assistant.model.dto.XunfeiResponse;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class XunfeiWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(XunfeiWebSocketClient.class);
    private final Gson gson = new Gson();
    private final XunfeiWebSocketHandler handler;

    // 状态控制
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    // 转写结果
    private final StringBuilder fullTranscription = new StringBuilder();
    private String sessionId;

    public XunfeiWebSocketClient(URI serverUri, XunfeiWebSocketHandler handler) {
        super(serverUri);
        this.handler = handler;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        isConnected.set(true);
        logger.info("【连接成功】WebSocket握手完成");
        connectLatch.countDown();
        handler.onConnected();
    }

    @Override
    public void onMessage(String message) {
        logger.debug("【接收消息】{}", message);

        try {
            XunfeiResponse response = gson.fromJson(message, XunfeiResponse.class);

            // 保存sessionId
            if (response.getSid() != null) {
                this.sessionId = response.getSid();
            }

            String action = response.getAction();

            if ("started".equals(action)) {
                logger.info("【握手成功】服务端就绪，session ID: {}", response.getSid());
                handler.onHandshakeSuccess(response.getSid());

            } else if ("result".equals(action)) {
                // 解析转写结果
                String text = parseTranscriptionResult(response);
                if (text != null && !text.isEmpty()) {
                    // 判断是否为最终结果
                    boolean isFinal = false;
                    if (response.getData() != null && response.getData().getCn() != null
                            && response.getData().getCn().getSt() != null) {
                        isFinal = response.getData().getCn().getSt().getType() == 0;
                    }

                    // 只有确定性结果才追加到完整文本
                    if (isFinal) {
                        fullTranscription.append(text);
                    }

                    handler.onTranscriptionResult(text, isFinal);
                    logger.info("【转写结果】{} (确定性: {})", text, isFinal);
                }

                // 检查是否为最后一帧
                if (response.getData() != null && Boolean.TRUE.equals(response.getData().getLs())) {
                    logger.info("【转写完成】收到最终结果");
                    handler.onTranscriptionComplete(fullTranscription.toString());
                }

            } else if ("error".equals(action)) {
                logger.error("【讯飞错误】code={}, desc={}", response.getCode(), response.getDesc());
                handler.onError(new Exception("讯飞错误: " + response.getCode() + " - " + response.getDesc()));
            }

        } catch (Exception e) {
            // 可能是非JSON消息
            logger.warn("【接收异常】非JSON消息或解析失败: {}",
                    message.length() > 100 ? message.substring(0, 100) + "..." : message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isConnected.set(false);
        logger.info("【连接关闭】代码: {}, 原因: {}, 远程关闭: {}", code, reason, remote);
        closeLatch.countDown();
        handler.onClosed(code, reason);
    }

    @Override
    public void onError(Exception ex) {
        logger.error("【WebSocket错误】{}", ex.getMessage(), ex);
        handler.onError(ex);
    }

    /**
     * 发送音频数据（二进制）
     */
    public void sendAudioFrame(byte[] audioData) {
        if (isConnected.get() && isOpen()) {
            send(audioData);
            logger.debug("【发送音频】{} bytes", audioData.length);
        } else {
            logger.warn("【发送失败】WebSocket未连接");
        }
    }

    /**
     * 发送结束标记（JSON格式）
     */
    public void sendEndFlag() {
        if (isConnected.get() && isOpen()) {
            JsonObject endMsg = new JsonObject();
            endMsg.addProperty("end", true);
            if (sessionId != null && !sessionId.isEmpty()) {
                endMsg.addProperty("sessionId", sessionId);
            }
            String endMsgStr = endMsg.toString();
            send(endMsgStr);
            logger.info("【发送结束】已发送结束标记: {}", endMsgStr);
        }
    }

    /**
     * 等待连接建立
     */
    public boolean awaitConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectLatch.await(timeout, unit);
    }

    /**
     * 等待连接关闭
     */
    public boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException {
        return closeLatch.await(timeout, unit);
    }

    /**
     * 解析转写结果文本
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
                                if (cw.getW() != null) {
                                    result.append(cw.getW());
                                }
                            }
                        }
                    }
                }
            }
        }

        return result.toString();
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFullTranscription() {
        return fullTranscription.toString();
    }
}
