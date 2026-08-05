package com.c203.limit.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider.IssuedToken;
import com.c203.limit.global.security.JwtTokenProvider.TokenClaims;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 발급→검증 왕복이 정확한지, 그리고 위변조/타입불일치/형식오류/만료 각각이 올바른 ErrorCode로
 * 거부되는지 확인한다. 서명·만료 검사는 보안에 직결되는 로직이라 실제 HMAC 서명을 그대로 태운다.
 */
class JwtTokenProviderTests {

    private static final String SECRET = "test-only-jwt-secret-must-be-32-bytes-or-more";

    private JwtTokenProvider provider(Duration accessTtl, Duration refreshTtl) {
        return new JwtTokenProvider(new ObjectMapper(), SECRET, accessTtl, refreshTtl);
    }

    private JwtTokenProvider provider() {
        return provider(Duration.ofMinutes(30), Duration.ofDays(14));
    }

    @Test
    void issueAccessThenParseRoundTripsAllClaims() {
        JwtTokenProvider provider = provider();

        IssuedToken issued = provider.issueAccess(42L, "MEMBER", Set.of("ROLE_USER"));
        TokenClaims claims = provider.parse(issued.value(), "access");

        assertThat(claims.tokenId()).isEqualTo(issued.tokenId());
        assertThat(claims.subjectId()).isEqualTo(42L);
        assertThat(claims.accountType()).isEqualTo("MEMBER");
        assertThat(claims.roles()).containsExactly("ROLE_USER");
    }

    @Test
    void issueRefreshThenParseRoundTripsAllClaims() {
        JwtTokenProvider provider = provider();

        IssuedToken issued = provider.issueRefresh(7L, "ADMIN", Set.of("ROLE_ADMIN"));
        TokenClaims claims = provider.parse(issued.value(), "refresh");

        assertThat(claims.subjectId()).isEqualTo(7L);
        assertThat(claims.accountType()).isEqualTo("ADMIN");
    }

    @Test
    void parseRejectsTokenWhenExpectedTypeDoesNotMatchIssuedType() {
        JwtTokenProvider provider = provider();
        IssuedToken issued = provider.issueAccess(42L, "MEMBER", Set.of());

        assertThatThrownBy(() -> provider.parse(issued.value(), "refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void parseRejectsMalformedTokenThatDoesNotHaveThreeParts() {
        JwtTokenProvider provider = provider();

        assertThatThrownBy(() -> provider.parse("not-a-jwt", "access"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void parseRejectsTokenWithATamperedSignature() {
        JwtTokenProvider provider = provider();
        IssuedToken issued = provider.issueAccess(42L, "MEMBER", Set.of());
        String[] parts = issued.value().split("\\.");
        String tamperedSignature = parts[2].equals("aaaa") ? "bbbb" : "aaaa";
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThatThrownBy(() -> provider.parse(tampered, "access"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void parseRejectsTokenWithAModifiedPayload() {
        // 서명은 header.payload 전체에 대해 계산되므로, payload만 다른 유효 base64로 바꿔치기해도
        // 서명 검증에서 걸려야 한다(페이로드 위조 방어).
        JwtTokenProvider provider = provider();
        IssuedToken issuedA = provider.issueAccess(1L, "MEMBER", Set.of());
        IssuedToken issuedB = provider.issueAccess(2L, "MEMBER", Set.of());
        String[] partsA = issuedA.value().split("\\.");
        String[] partsB = issuedB.value().split("\\.");
        String swapped = partsA[0] + "." + partsB[1] + "." + partsA[2];

        assertThatThrownBy(() -> provider.parse(swapped, "access"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void parseRejectsAnAlreadyExpiredToken() {
        JwtTokenProvider expiredProvider = provider(Duration.ofSeconds(-60), Duration.ofDays(14));
        IssuedToken issued = expiredProvider.issueAccess(42L, "MEMBER", Set.of());

        assertThatThrownBy(() -> expiredProvider.parse(issued.value(), "access"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void constructorRejectsSecretsShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                        new ObjectMapper(), "too-short-secret", Duration.ofMinutes(30), Duration.ofDays(14)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNullSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                        new ObjectMapper(), null, Duration.ofMinutes(30), Duration.ofDays(14)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessTtlAndRefreshTtlExposeTheConfiguredDurations() {
        JwtTokenProvider provider = provider(Duration.ofMinutes(15), Duration.ofDays(7));

        assertThat(provider.accessTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(provider.refreshTtl()).isEqualTo(Duration.ofDays(7));
    }
}
