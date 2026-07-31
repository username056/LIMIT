package com.c203.limit.domain.rtc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.call.domain.AppointmentStatus;
import com.c203.limit.domain.call.entity.CallAppointment;
import com.c203.limit.domain.call.event.CallAppointmentNotificationAction;
import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.call.repository.CallAppointmentRepository;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import com.c203.limit.domain.rtc.dto.request.CreateCallRequest;
import com.c203.limit.domain.rtc.dto.request.EndRtcSessionRequest;
import com.c203.limit.domain.rtc.dto.request.RtcChecklistResultRequest;
import com.c203.limit.domain.rtc.dto.request.UpdateCallRequest;
import com.c203.limit.domain.rtc.entity.RtcSession;
import com.c203.limit.domain.rtc.repository.RtcSessionChecklistResultRepository;
import com.c203.limit.domain.rtc.repository.RtcSessionRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class RtcCallServiceTests {
    private CallAppointmentRepository appointmentRepository;
    private RtcSessionRepository sessionRepository;
    private ChatRoomRepository chatRoomRepository;
    private MemberRepository memberRepository;
    private ApplicationEventPublisher eventPublisher;
    private RtcCallService service;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(CallAppointmentRepository.class);
        sessionRepository = mock(RtcSessionRepository.class);
        chatRoomRepository = mock(ChatRoomRepository.class);
        memberRepository = mock(MemberRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service =
                new RtcCallService(
                        appointmentRepository,
                        sessionRepository,
                        mock(RtcSessionChecklistResultRepository.class),
                        chatRoomRepository,
                        mock(ListingChecklistItemRepository.class),
                        new RtcJoinTokenStore(),
                        eventPublisher,
                        memberRepository,
                        "stun:example.test:3478",
                        "",
                        "",
                        "");
    }

    @Test
    void allowsNewAppointmentAfterAcceptedSessionExpired() {
        CallAppointment previous =
                appointment(
                        1L,
                        AppointmentStatus.ACCEPTED,
                        LocalDateTime.now().minusMinutes(40));
        RtcSession expiredSession =
                RtcSession.waiting(
                        1L, 10L, 100L, 20L, 30L, LocalDateTime.now().minusMinutes(10));
        CallAppointment created =
                appointment(
                        2L,
                        AppointmentStatus.PROPOSED,
                        LocalDateTime.now().plusMinutes(10));
        stubRequest(previous, expiredSession, created);

        var response =
                service.request(
                        10L,
                        20L,
                        new CreateCallRequest(LocalDateTime.now().plusMinutes(10), null));

        assertThat(response.callId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.PROPOSED.name());
    }

    @Test
    void rejectsNewAppointmentWhileAcceptedSessionIsActive() {
        CallAppointment previous =
                appointment(
                        1L,
                        AppointmentStatus.ACCEPTED,
                        LocalDateTime.now().minusMinutes(5));
        RtcSession activeSession =
                RtcSession.waiting(
                        1L, 10L, 100L, 20L, 30L, LocalDateTime.now().plusMinutes(25));
        stubRequest(previous, activeSession, null);

        assertThatThrownBy(
                        () ->
                                service.request(
                                        10L,
                                        20L,
                                        new CreateCallRequest(
                                                LocalDateTime.now().plusMinutes(10), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_ACTIVE_APPOINTMENT_EXISTS));
    }

    @Test
    void rejectsNewAppointmentWhenAcceptedSessionHasNoExpiration() {
        CallAppointment previous =
                appointment(
                        1L,
                        AppointmentStatus.ACCEPTED,
                        LocalDateTime.now().minusMinutes(5));
        RtcSession activeSession =
                RtcSession.waiting(
                        1L, 10L, 100L, 20L, 30L, LocalDateTime.now().plusMinutes(25));
        ReflectionTestUtils.setField(activeSession, "expiresAt", null);
        stubRequest(previous, activeSession, null);

        assertThatThrownBy(
                        () ->
                                service.request(
                                        10L,
                                        20L,
                                        new CreateCallRequest(
                                                LocalDateTime.now().plusMinutes(10), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_ACTIVE_APPOINTMENT_EXISTS));
    }

    @Test
    void allowsNewAppointmentAfterProposedAppointmentWindowExpired() {
        CallAppointment previous =
                appointment(
                        1L,
                        AppointmentStatus.PROPOSED,
                        LocalDateTime.now().minusMinutes(31));
        CallAppointment created =
                appointment(
                        2L,
                        AppointmentStatus.PROPOSED,
                        LocalDateTime.now().plusMinutes(10));
        stubRequest(previous, null, created);

        var response =
                service.request(
                        10L,
                        20L,
                        new CreateCallRequest(LocalDateTime.now().plusMinutes(10), null));

        assertThat(response.callId()).isEqualTo(2L);
    }

    @Test
    void rejectsAcceptanceAfterProposedAppointmentWindowExpired() {
        CallAppointment appointment =
                appointment(
                        1L,
                        AppointmentStatus.PROPOSED,
                        LocalDateTime.now().minusMinutes(31));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(
                        () ->
                                service.respond(
                                        1L,
                                        30L,
                                        new com.c203.limit.domain.rtc.dto.request.RespondCallRequest(
                                                true, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_EXPIRED));
    }

    private void stubRequest(
            CallAppointment previous, RtcSession session, CallAppointment created) {
        ChatRoom room = ChatRoom.create(100L, 20L, 30L);
        ReflectionTestUtils.setField(room, "id", 10L);
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(appointmentRepository.findByChatRoomIdAndStatusIn(
                        10L, List.of(AppointmentStatus.PROPOSED, AppointmentStatus.ACCEPTED)))
                .thenReturn(List.of(previous));
        if (session != null) {
            when(sessionRepository.findByCallAppointmentIdIn(List.of(previous.getId())))
                    .thenReturn(List.of(session));
        }
        if (created != null) {
            when(appointmentRepository.save(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(created);
        }
    }

    private CallAppointment appointment(
            Long id, AppointmentStatus status, LocalDateTime scheduledAt) {
        CallAppointment appointment =
                CallAppointment.propose(10L, 20L, 30L, scheduledAt, null);
        ReflectionTestUtils.setField(appointment, "id", id);
        if (status == AppointmentStatus.ACCEPTED) {
            appointment.accept(30L);
        }
        return appointment;
    }

    @Test
    void expiresSessionWhenParticipantTriesToRejoinAfterTtl() {
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.join(10L, 4L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_EXPIRED));
        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.EXPIRED);
    }

    @Test
    void rejectsRejoinAfterSessionEnded() {
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().plusMinutes(10));
        session.end(com.c203.limit.domain.rtc.domain.RtcEndReason.COMPLETED, null);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.join(10L, 5L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_CLOSED));
    }

    @Test
    void rejectsDuplicateChecklistItemsBeforeSavingEndResult() {
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().plusMinutes(10));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        EndRtcSessionRequest request =
                new EndRtcSessionRequest(
                        "COMPLETED",
                        null,
                        List.of(
                                new RtcChecklistResultRequest(100L, true, null),
                                new RtcChecklistResultRequest(100L, false, "재확인 필요")));

        assertThatThrownBy(() -> service.end(10L, 5L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void proposerUpdatesProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);
        LocalDateTime changedAt = LocalDateTime.now().plusMinutes(30);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        var response = service.update(1L, 20L, new UpdateCallRequest(changedAt, "외관 확인"));

        assertThat(response.scheduledAt()).isEqualTo(changedAt);
        assertThat(response.memo()).isEqualTo("외관 확인");
    }

    @Test
    void respondentCannotUpdateProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        1L,
                                        30L,
                                        new UpdateCallRequest(
                                                LocalDateTime.now().plusMinutes(30), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_INVALID_STATE));
    }

    @Test
    void proposerCancelsProposedCall() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        var response = service.cancel(1L, 20L, "일정 취소");

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELED.name());
        assertThat(response.cancelReason()).isEqualTo("일정 취소");
        verify(eventPublisher)
                .publishEvent(
                        org.mockito.ArgumentMatchers.<Object>argThat(
                                event ->
                                        event instanceof CallAppointmentUpdatedNotificationEvent
                                                notification
                                                && notification.action()
                                                        == CallAppointmentNotificationAction
                                                                .CANCELED));
    }

    @Test
    void acceptedCallCannotBeCanceled() {
        CallAppointment appointment = CallAppointment.propose(
                10L, 20L, 30L, LocalDateTime.now().plusMinutes(10), null);
        appointment.accept(30L);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.cancel(1L, 20L, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_INVALID_STATE));
    }
}
