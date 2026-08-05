package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * isValid()가 subjectId/accountType까지 정확히 일치해야 true를 주는지, revoke는 단건만,
 * revokeAll은 같은 subjectId+accountType 조합만 골라 지우는지(다른 조합은 건드리지 않는지) 확인한다.
 */
class InMemoryRefreshTokenStoreTests {

    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Test
    void isValidReturnsTrueForAMatchingUnexpiredToken() {
        store.save("token-1", 42L, "MEMBER", Duration.ofMinutes(10));

        assertThat(store.isValid("token-1", 42L, "MEMBER")).isTrue();
    }

    @Test
    void isValidReturnsFalseForAnUnknownToken() {
        assertThat(store.isValid("never-saved", 42L, "MEMBER")).isFalse();
    }

    @Test
    void isValidReturnsFalseWhenSubjectIdDoesNotMatch() {
        store.save("token-1", 42L, "MEMBER", Duration.ofMinutes(10));

        assertThat(store.isValid("token-1", 99L, "MEMBER")).isFalse();
    }

    @Test
    void isValidReturnsFalseWhenAccountTypeDoesNotMatch() {
        store.save("token-1", 42L, "MEMBER", Duration.ofMinutes(10));

        assertThat(store.isValid("token-1", 42L, "ADMIN")).isFalse();
    }

    @Test
    void isValidReturnsFalseForAnExpiredToken() {
        store.save("token-1", 42L, "MEMBER", Duration.ofSeconds(-1));

        assertThat(store.isValid("token-1", 42L, "MEMBER")).isFalse();
    }

    @Test
    void revokeRemovesOnlyTheGivenToken() {
        store.save("token-1", 42L, "MEMBER", Duration.ofMinutes(10));
        store.save("token-2", 42L, "MEMBER", Duration.ofMinutes(10));

        store.revoke("token-1");

        assertThat(store.isValid("token-1", 42L, "MEMBER")).isFalse();
        assertThat(store.isValid("token-2", 42L, "MEMBER")).isTrue();
    }

    @Test
    void revokeAllRemovesEveryTokenForTheSameSubjectAndAccountTypeOnly() {
        store.save("member-token-1", 42L, "MEMBER", Duration.ofMinutes(10));
        store.save("member-token-2", 42L, "MEMBER", Duration.ofMinutes(10));
        store.save("other-subject-token", 99L, "MEMBER", Duration.ofMinutes(10));
        store.save("other-type-token", 42L, "ADMIN", Duration.ofMinutes(10));

        store.revokeAll(42L, "MEMBER");

        assertThat(store.isValid("member-token-1", 42L, "MEMBER")).isFalse();
        assertThat(store.isValid("member-token-2", 42L, "MEMBER")).isFalse();
        // 다른 subjectId나 다른 accountType 조합은 영향받지 않아야 한다.
        assertThat(store.isValid("other-subject-token", 99L, "MEMBER")).isTrue();
        assertThat(store.isValid("other-type-token", 42L, "ADMIN")).isTrue();
    }
}
