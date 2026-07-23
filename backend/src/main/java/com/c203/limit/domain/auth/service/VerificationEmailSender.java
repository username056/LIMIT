package com.c203.limit.domain.auth.service;

import java.time.Duration;

public interface VerificationEmailSender {
    boolean isAvailable();

    void send(String recipient, String verificationUrl, Duration expiresIn);
}
