package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * generate()가 매번 다른 값을 주는 URL-safe base64 토큰을 만드는지, hash()가 동일 입력에 대해
 * 결정적이고(같은 값→같은 해시) 다른 입력은 다른 해시를 내는지 확인한다. 같은 패키지의
 * package-private 클래스라 패키지 내부 테스트로만 접근 가능하다.
 */
class OpaqueTokenSupportTests {

    @Test
    void generateProducesUrlSafeBase64WithoutPadding() {
        String token = OpaqueTokenSupport.generate();

        assertThat(token).doesNotContain("+", "/", "=");
        // 32바이트를 패딩 없는 URL-safe base64로 인코딩하면 43자가 된다.
        assertThat(token).hasSize(43);
    }

    @Test
    void generateProducesDifferentValuesOnEachCall() {
        long distinctCount = IntStream.range(0, 20)
                .mapToObj(i -> OpaqueTokenSupport.generate())
                .distinct()
                .count();

        assertThat(distinctCount).isEqualTo(20);
    }

    @Test
    void hashIsDeterministicForTheSameInput() {
        assertThat(OpaqueTokenSupport.hash("same-value")).isEqualTo(OpaqueTokenSupport.hash("same-value"));
    }

    @Test
    void hashDiffersForDifferentInputs() {
        assertThat(OpaqueTokenSupport.hash("value-a")).isNotEqualTo(OpaqueTokenSupport.hash("value-b"));
    }

    @Test
    void hashMatchesKnownSha256Base64UrlEncoding() throws java.security.NoSuchAlgorithmException {
        // "limit"의 SHA-256을 별도로 계산해 하드코딩값과 대조 — 알고리즘/인코딩이 바뀌면 이 값이 깨진다.
        String expected = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                        .digest("limit".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(OpaqueTokenSupport.hash("limit")).isEqualTo(expected);
    }
}
