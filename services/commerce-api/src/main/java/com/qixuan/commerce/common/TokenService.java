package com.qixuan.commerce.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 无状态 token，格式：base64url(payload) + "." + base64url(签名)
 *
 * 和 JWT 的原理完全相同（服务端不存、签名防篡改），差别只是 JWT 的 payload 是一段 JSON，
 * 这里用 "userId:过期时间戳" 的紧凑格式，省掉序列化。
 *
 * 安全要点：
 * 1. 签名用 HmacSHA256，密钥只在服务端，客户端改不了 payload（改了签名就对不上）。
 * 2. 比较签名用 MessageDigest.isEqual（常量时间），避免逐字节比较被时序攻击猜出签名。
 */
@Component
public class TokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(
            @Value("${app.auth.secret}") String secret,
            @Value("${app.auth.ttl-seconds:604800}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    /** 签发 token */
    public String issue(Long userId) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = userId + ":" + expiresAt;
        String body = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + ENCODER.encodeToString(sign(body));
    }

    /**
     * 校验 token。
     *
     * @return 校验通过返回 userId；签名不对、格式错误或已过期返回 null
     */
    public Long verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return null;
        }

        String body = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        byte[] actual;
        try {
            actual = DECODER.decode(signature);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!MessageDigest.isEqual(sign(body), actual)) {
            return null;
        }

        String payload;
        try {
            payload = new String(DECODER.decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        int colon = payload.indexOf(':');
        if (colon <= 0) {
            return null;
        }

        try {
            Long userId = Long.parseLong(payload.substring(0, colon));
            long expiresAt = Long.parseLong(payload.substring(colon + 1));
            if (expiresAt < Instant.now().getEpochSecond()) {
                return null;
            }
            return userId;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("token 签名失败", ex);
        }
    }
}
