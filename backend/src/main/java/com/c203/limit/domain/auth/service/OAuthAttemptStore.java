package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.entity.SocialProvider;
import java.time.Duration;
import java.util.Optional;

public interface OAuthAttemptStore {
    void save(String stateHash, OAuthAttempt attempt, Duration ttl);

    Optional<OAuthAttempt> consume(String stateHash);

    record OAuthAttempt(SocialProvider provider, String redirectUri, Long memberId) {}
}
