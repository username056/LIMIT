package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.entity.SocialProvider;
import java.time.Duration;
import java.util.Optional;

public interface SocialSignupSessionStore {
    void save(String tokenHash, SocialSignupSession session, Duration ttl);

    Optional<SocialSignupSession> consume(String tokenHash);

    record SocialSignupSession(
            SocialProvider provider,
            String providerUserId,
            String email,
            String suggestedNickname) {}
}
