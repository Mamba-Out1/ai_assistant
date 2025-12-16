package com.medical.assistant.util;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

public class SignatureUtil {

    private static final Logger logger = LoggerFactory.getLogger(SignatureUtil.class);
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * 生成讯飞WebSocket连接URL
     */
    public static String generateWebSocketUrl(String baseUrl, String appId, String apiKey,
                                              String apiSecret, Map<String, String> params) {
        try {
            // 添加基础参数
            TreeMap<String, String> allParams = new TreeMap<>();
            allParams.put("appId", appId);
            allParams.put("accessKeyId", apiKey);

            // 生成UTC时间
            String utc = generateUtcTime();
            allParams.put("utc", utc);

            // 添加业务参数
            if (params != null) {
                allParams.putAll(params);
            }

            // 生成baseString
            String baseString = generateBaseString(allParams);
            logger.debug("BaseString: {}", baseString);

            // 生成签名
            String signature = generateSignature(baseString, apiSecret);
            logger.debug("Signature: {}", signature);

            // 添加签名到参数
            allParams.put("signature", signature);

            // 构建URL
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append("?");

            boolean first = true;
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(urlEncode(entry.getKey()))
                        .append("=")
                        .append(urlEncode(entry.getValue()));
                first = false;
            }

            String finalUrl = urlBuilder.toString();
            logger.debug("Final WebSocket URL: {}", finalUrl);

            return finalUrl;

        } catch (Exception e) {
            logger.error("生成WebSocket URL失败", e);
            throw new RuntimeException("生成WebSocket URL失败", e);
        }
    }

    /**
     * 生成UTC时间字符串
     */
    private static String generateUtcTime() {
        ZonedDateTime now = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
        return now.format(formatter);
    }

    /**
     * 生成baseString（参数按字母顺序排序并拼接）
     */
    private static String generateBaseString(TreeMap<String, String> params) {
        StringBuilder baseString = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                baseString.append("&");
            }
            baseString.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
            first = false;
        }

        return baseString.toString();
    }

    /**
     * 使用HmacSHA1算法生成签名
     */
    private static String generateSignature(String baseString, String apiSecret)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {

        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                apiSecret.getBytes("UTF-8"), HMAC_SHA1_ALGORITHM);
        mac.init(secretKeySpec);

        byte[] rawHmac = mac.doFinal(baseString.getBytes("UTF-8"));
        return Base64.encodeBase64String(rawHmac);
    }

    /**
     * URL编码
     */
    private static String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("URL编码失败", e);
        }
    }
}