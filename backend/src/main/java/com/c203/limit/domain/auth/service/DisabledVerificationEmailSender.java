package com.c203.limit.domain.auth.service;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "limit.auth.email-verification.delivery-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledVerificationEmailSender implements VerificationEmailSender {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void send(String recipient, String verificationUrl, Duration expiresIn) {
        throw new IllegalStateException("Email verification delivery is disabled");
    }
}
