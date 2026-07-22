package com.c203.limit.domain.auth.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "limit.auth.email-verification.delivery-enabled",
        havingValue = "true")
public class SmtpVerificationEmailSender implements VerificationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${limit.auth.email-verification.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void send(String recipient, String verificationUrl, Duration expiresIn) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("[LIMIT] 이메일 인증");
        message.setText(
                "아래 링크에서 이메일 인증을 완료해 주세요.\n\n"
                        + verificationUrl
                        + "\n\n유효 시간: "
                        + expiresIn.toMinutes()
                        + "분");
        mailSender.send(message);
    }
}
