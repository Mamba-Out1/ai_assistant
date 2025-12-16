package com.medical.assistant.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class SignatureUtil {

    private static final Logger logger = LoggerFactory.getLogger(SignatureUtil.class);
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * 生成讯飞WebSocket连接URL
     */
    public static String generateWebSocketUrl(String baseUrl, String appId, String apiKey,
                                              String apiSecret, Map<String, String> businessParams) {
        try {
            // 使用TreeMap保证字典序排序
            TreeMap<String, String> params = new TreeMap<>();

            // 添加业务参数
            if (businessParams != null) {
                params.putAll(businessParams);
            }

            // 添加鉴权参数
            params.put("accessKeyId", apiKey);
            params.put("appId", appId);
            params.put("uuid", UUID.randomUUID().toString().replace("-", ""));
            params.put("utc", getUtcTime());

            // 计算签名（不包含signature参数）
            String signature = calculateSignature(params, apiSecret);
            params.put("signature", signature);

            // 构建URL
            String paramsStr = buildParamsString(params);
            String fullUrl = baseUrl + "?" + paramsStr;

            logger.info("【连接信息】完整URL：{}", fullUrl);

            return fullUrl;

        } catch (Exception e) {
            logger.error("生成WebSocket URL失败", e);
            throw new RuntimeException("生成WebSocket URL失败", e);
        }
    }

    /**
     * 生成UTC时间字符串（yyyy-MM-dd'T'HH:mm:ss+0800）
     */
    private static String getUtcTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new Date());
    }

    /**
     * 计算HMAC-SHA1签名
     */
    private static String calculateSignature(TreeMap<String, String> params, String apiSecret) {
        try {
            // 构建基础字符串
            StringBuilder baseStr = new StringBuilder();
            boolean first = true;

            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 跳过signature参数
                if ("signature".equals(key)) continue;

                // 过滤空值
                if (value == null || value.trim().isEmpty()) continue;

                if (!first) {
                    baseStr.append("&");
                }

                baseStr.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()))
                        .append("=")
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
                first = false;
            }

            logger.debug("签名基础字符串: {}", baseStr.toString());

            // HMAC-SHA1计算
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1_ALGORITHM);
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(baseStr.toString().getBytes(StandardCharsets.UTF_8));

            // Base64编码
            return Base64.getEncoder().encodeToString(signBytes);

        } catch (Exception e) {
            throw new RuntimeException("计算签名失败", e);
        }
    }

    /**
     * 构建参数字符串
     */
    private static String buildParamsString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            try {
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException e) {
                // UTF-8编码总是支持的
                e.printStackTrace();
            }
            first = false;
        }

        return sb.toString();
    }
}
