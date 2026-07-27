package com.c203.limit.domain.auth.service;

import java.time.Duration;

public interface PasswordResetEmailSender {
    boolean isAvailable();

    void send(String recipient, String resetUrl, Duration expiresIn);
}
