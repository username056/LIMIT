package com.c203.limit.domain.auth.service;

import java.time.Duration;
import java.util.Optional;

public interface PasswordResetTokenStore {
    void save(String tokenHash, Long memberId, Duration ttl);

    Optional<Long> consume(String tokenHash);
}
