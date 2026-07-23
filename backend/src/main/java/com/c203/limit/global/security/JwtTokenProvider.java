package com.c203.limit.global.security;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${limit.security.jwt.secret:local-only-change-this-jwt-secret-32bytes}")
                    String secret,
            @Value("${limit.security.jwt.access-ttl:PT30M}") Duration accessTtl,
            @Value("${limit.security.jwt.refresh-ttl:P14D}") Duration refreshTtl) {
        this.objectMapper = objectMapper;
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public IssuedToken issueAccess(Long id, String accountType, Set<String> roles) {
        return issue(id, accountType, roles, "access", accessTtl);
    }

    public IssuedToken issueRefresh(Long id, String accountType, Set<String> roles) {
        return issue(id, accountType, roles, "refresh", refreshTtl);
    }

    public TokenClaims parse(String token, String expectedType) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3
                    || !constantTimeEquals(parts[2], sign(parts[0] + "." + parts[1]))) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            JsonNode payload = objectMapper.readTree(DECODER.decode(parts[1]));
            if (!expectedType.equals(payload.path("typ").asText()))
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            if (Instant.ofEpochSecond(payload.path("exp").asLong()).isBefore(Instant.now())) {
                throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
            }
            Set<String> roles =
                    objectMapper.convertValue(
                            payload.path("roles"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(Set.class, String.class));
            return new TokenClaims(
                    payload.path("jti").asText(),
                    payload.path("sid").asLong(),
                    payload.path("act").asText(),
                    roles);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    private IssuedToken issue(
            Long id, String accountType, Set<String> roles, String type, Duration ttl) {
        try {
            Instant expiresAt = Instant.now().plus(ttl);
            String tokenId = UUID.randomUUID().toString();
            String header =
                    ENCODER.encodeToString(
                            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String json =
                    objectMapper.writeValueAsString(
                            new Payload(
                                    tokenId,
                                    id,
                                    accountType,
                                    roles,
                                    type,
                                    expiresAt.getEpochSecond()));
            String payload = ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String content = header + "." + payload;
            return new IssuedToken(content + "." + sign(content), tokenId, expiresAt);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 발급에 실패했습니다.", exception);
        }
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private record Payload(
            String jti, Long sid, String act, Set<String> roles, String typ, long exp) {}

    public record IssuedToken(String value, String tokenId, Instant expiresAt) {}

    public record TokenClaims(
            String tokenId, Long subjectId, String accountType, Set<String> roles) {}
}
