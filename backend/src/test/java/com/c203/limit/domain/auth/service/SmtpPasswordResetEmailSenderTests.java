package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Disabled 구현과 달리 SMTP 구현은 실제 발송 가능 상태를 보고하고 메일 본문을 직접 조립한다. 발신자/수신자/제목/본문 구성과 만료 시간 표기, SMTP 실패 시
 * 예외가 그대로 전파되는지를 확인한다. (테스트에는 더미 링크만 사용한다.)
 */
@ExtendWith(MockitoExtension.class)
class SmtpPasswordResetEmailSenderTests {

    private static final String RESET_URL = "https://limit.example.com/reset?token=dummy-token";

    @Mock JavaMailSender mailSender;
    SmtpPasswordResetEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpPasswordResetEmailSender(mailSender, "no-reply@limit.example.com");
    }

    @Test
    void reportsItselfAsAvailableSoTheServiceActuallyDeliversTheEmail() {
        assertThat(sender.isAvailable()).isTrue();
    }

    @Test
    void sendComposesTheMessageFromTheConfiguredSenderAddressAndTheResetLink() {
        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        sender.send("user@example.com", RESET_URL, Duration.ofMinutes(30));

        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@limit.example.com");
        assertThat(message.getValue().getTo()).containsExactly("user@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("[LIMIT] 비밀번호 재설정");
        assertThat(message.getValue().getText()).contains(RESET_URL).contains("유효 시간: 30분");
    }

    @Test
    void sendReportsTheExpiryTruncatedToWholeMinutes() {
        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        sender.send("user@example.com", RESET_URL, Duration.ofSeconds(90));

        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getText()).contains("유효 시간: 1분");
    }

    @Test
    void sendPropagatesTheFailureWhenTheSmtpServerRejectsTheMessage() {
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(
                        () -> sender.send("user@example.com", RESET_URL, Duration.ofMinutes(30)))
                .isInstanceOf(MailSendException.class);
    }
}
