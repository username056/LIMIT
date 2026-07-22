package com.c203.limit.domain.auth.service;

import java.time.Duration;

public interface RefreshTokenStore {
    void save(String tokenId, Long subjectId, String accountType, Duration ttl);

    boolean isValid(String tokenId, Long subjectId, String accountType);

    void revoke(String tokenId);

    void revokeAll(Long subjectId, String accountType);
}
