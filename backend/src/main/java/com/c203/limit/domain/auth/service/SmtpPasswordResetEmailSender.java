package com.c203.limit.domain.auth.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "limit.auth.password-reset.delivery-enabled", havingValue = "true")
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetEmailSender.class);
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetEmailSender(
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
    public void send(String recipient, String resetUrl, Duration expiresIn) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("[LIMIT] 비밀번호 재설정");
        message.setText(
                "아래 링크에서 비밀번호를 재설정해 주세요.\n\n"
                        + resetUrl
                        + "\n\n유효 시간: "
                        + expiresIn.toMinutes()
                        + "분");
        mailSender.send(message);
        log.info("Password reset email sent via SMTP");
    }
}
