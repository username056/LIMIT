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
}
