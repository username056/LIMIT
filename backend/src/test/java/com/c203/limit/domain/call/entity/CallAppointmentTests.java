package com.c203.limit.domain.call.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.call.domain.AppointmentStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CallAppointmentTests {
    @Test
    void respondentAcceptsProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), "배터리 확인");

        appointment.accept(30L);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ACCEPTED);
        assertThat(appointment.getRespondedAt()).isNotNull();
    }

    @Test
    void proposerCannotAcceptOwnRequest() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);

        assertThatThrownBy(() -> appointment.accept(20L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void proposerUpdatesProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), "배터리 확인");
        LocalDateTime changedAt = LocalDateTime.now().plusMinutes(30);

        appointment.update(20L, changedAt, "외관 확인");

        assertThat(appointment.getScheduledAt()).isEqualTo(changedAt);
        assertThat(appointment.getMemo()).isEqualTo("외관 확인");
    }

    @Test
    void respondentCannotUpdateProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);

        assertThatThrownBy(
                        () ->
                                appointment.update(
                                        30L, LocalDateTime.now().plusMinutes(30), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void proposerCancelsProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);

        appointment.cancel(20L, "시간 조율 필요");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELED);
        assertThat(appointment.getCancelReason()).isEqualTo("시간 조율 필요");
    }

    @Test
    void acceptedCallCannotBeCanceled() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);
        appointment.accept(30L);

        assertThatThrownBy(() -> appointment.cancel(20L, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
