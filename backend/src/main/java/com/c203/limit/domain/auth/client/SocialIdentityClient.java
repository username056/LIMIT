package com.c203.limit.domain.auth.client;

import com.c203.limit.domain.auth.entity.SocialProvider;

public interface SocialIdentityClient {
    SocialProvider provider();

    SocialIdentity exchange(String authorizationCode, String redirectUri, String state);

    record SocialIdentity(String providerUserId, String email, String nickname) {}
}
