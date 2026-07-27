package com.c203.limit.domain.auth.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "limit.auth.password-reset.delivery-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledPasswordResetEmailSender implements PasswordResetEmailSender {
    private static final Logger log =
            LoggerFactory.getLogger(DisabledPasswordResetEmailSender.class);

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void send(String recipient, String resetUrl, Duration expiresIn) {
        log.error("Password reset email sender invoked while delivery is disabled");
        throw new IllegalStateException("Password reset email delivery is disabled");
    }
}
