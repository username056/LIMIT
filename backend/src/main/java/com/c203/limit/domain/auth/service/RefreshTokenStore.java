package com.c203.limit.domain.auth.service;

import java.time.Duration;

public interface RefreshTokenStore {
    void save(String tokenId, Long subjectId, Duration ttl);
    boolean isValid(String tokenId, Long subjectId);
    void revoke(String tokenId);
    void revokeAll(Long subjectId);
}
