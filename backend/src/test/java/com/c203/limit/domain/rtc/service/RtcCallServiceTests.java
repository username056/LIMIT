package com.c203.limit.domain.rtc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.call.domain.AppointmentStatus;
import com.c203.limit.domain.call.entity.CallAppointment;
import com.c203.limit.domain.call.event.CallAppointmentNotificationAction;
import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.call.repository.CallAppointmentRepository;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import com.c203.limit.domain.rtc.dto.request.CreateCallRequest;
import com.c203.limit.domain.rtc.dto.request.EndRtcSessionRequest;
import com.c203.limit.domain.rtc.dto.request.MarkRtcConnectedRequest;
import com.c203.limit.domain.rtc.dto.request.RtcChecklistResultRequest;
import com.c203.limit.domain.rtc.dto.request.UpdateCallRequest;
import com.c203.limit.domain.rtc.entity.RtcSession;
import com.c203.limit.domain.rtc.entity.RtcSessionChecklistResult;
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
    private RtcSessionChecklistResultRepository resultRepository;
    private ChatRoomRepository chatRoomRepository;
    private ListingChecklistItemRepository checklistRepository;
    private MemberRepository memberRepository;
    private ApplicationEventPublisher eventPublisher;
    private RtcCallService service;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(CallAppointmentRepository.class);
        sessionRepository = mock(RtcSessionRepository.class);
        resultRepository = mock(RtcSessionChecklistResultRepository.class);
        chatRoomRepository = mock(ChatRoomRepository.class);
        checklistRepository = mock(ListingChecklistItemRepository.class);
        memberRepository = mock(MemberRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = service("", "", "");
        CallAppointment accepted =
                appointment(1L, AppointmentStatus.ACCEPTED, LocalDateTime.now().minusMinutes(1));
        lenient().when(appointmentRepository.findById(1L)).thenReturn(Optional.of(accepted));
    }

    private RtcCallService service(String turnUrl, String turnUsername, String turnCredential) {
        return new RtcCallService(
                appointmentRepository,
                sessionRepository,
                resultRepository,
                chatRoomRepository,
                checklistRepository,
                new RtcJoinTokenStore(),
                eventPublisher,
                memberRepository,
                "stun:example.test:3478",
                turnUrl,
                turnUsername,
                turnCredential);
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
        if (status == AppointmentStatus.ACCEPTED || status == AppointmentStatus.COMPLETED) {
            appointment.accept(30L);
        }
        if (status == AppointmentStatus.COMPLETED) appointment.complete(LocalDateTime.now());
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
    void rejectsJoinBeforeScheduledAt() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(10);
        CallAppointment appointment =
                appointment(1L, AppointmentStatus.ACCEPTED, scheduledAt);
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, scheduledAt.plusMinutes(30));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.join(10L, 4L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_INVALID_STATE));
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

    @Test
    void returnsEmptyListWhenMemberHasNoAppointment() {
        when(appointmentRepository.findMine(20L)).thenReturn(List.of());

        assertThat(service.findMine(20L)).isEmpty();
        verify(sessionRepository, never()).findByCallAppointmentIdIn(anyList());
    }

    @Test
    void mapsSessionAndCounterpartNameOntoOwnAppointments() {
        LocalDateTime scheduledAt = LocalDateTime.now().minusMinutes(5);
        CallAppointment accepted = appointment(1L, AppointmentStatus.ACCEPTED, scheduledAt);
        RtcSession session = session(50L, 1L, scheduledAt.plusMinutes(30));
        when(appointmentRepository.findMine(20L)).thenReturn(List.of(accepted));
        when(sessionRepository.findByCallAppointmentIdIn(List.of(1L)))
                .thenReturn(List.of(session));
        when(memberRepository.findById(30L)).thenReturn(Optional.of(member("판매자")));

        var responses = service.findMine(20L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).rtcSessionId()).isEqualTo(50L);
        assertThat(responses.get(0).sessionExpiresAt()).isEqualTo(session.getExpiresAt());
        assertThat(responses.get(0).incoming()).isFalse();
        assertThat(responses.get(0).counterpartName()).isEqualTo("판매자");
    }

    @Test
    void omitsExpirationFromCompletedAppointment() {
        LocalDateTime scheduledAt = LocalDateTime.now().minusMinutes(5);
        CallAppointment completed =
                appointment(1L, AppointmentStatus.COMPLETED, scheduledAt);
        RtcSession session = session(50L, 1L, scheduledAt.plusMinutes(30));
        session.completeInspection(
                com.c203.limit.domain.rtc.domain.RtcEndReason.COMPLETED,
                null,
                LocalDateTime.now());
        when(appointmentRepository.findMine(20L)).thenReturn(List.of(completed));
        when(sessionRepository.findByCallAppointmentIdIn(List.of(1L)))
                .thenReturn(List.of(session));

        var response = service.findMine(20L).get(0);

        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED.name());
        assertThat(response.sessionExpiresAt()).isNull();
        assertThat(response.inspectionSubmittedAt()).isNotNull();
    }

    @Test
    void marksAppointmentIncomingForRespondentAndOmitsMissingCounterpartName() {
        CallAppointment proposed =
                appointment(1L, AppointmentStatus.PROPOSED, LocalDateTime.now().plusMinutes(10));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(proposed));
        when(sessionRepository.findByCallAppointmentId(1L)).thenReturn(Optional.empty());
        when(memberRepository.findById(20L)).thenReturn(Optional.empty());

        var response = service.findCall(1L, 30L);

        assertThat(response.incoming()).isTrue();
        assertThat(response.counterpartName()).isNull();
        assertThat(response.rtcSessionId()).isNull();
        assertThat(response.sessionExpiresAt()).isNull();
    }

    @Test
    void rejectsCallLookupForUnknownAppointment() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCall(1L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LISTING_NOT_FOUND));
    }

    @Test
    void rejectsCallLookupForNonParticipant() {
        CallAppointment proposed =
                appointment(1L, AppointmentStatus.PROPOSED, LocalDateTime.now().plusMinutes(10));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(proposed));

        assertThatThrownBy(() -> service.findCall(1L, 99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    @Test
    void rejectsSessionLookupForUnknownSession() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findSession(10L, 4L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_NOT_FOUND));
    }

    @Test
    void rejectsSessionLookupForNonParticipant() {
        when(sessionRepository.findById(10L))
                .thenReturn(Optional.of(session(10L, 1L, LocalDateTime.now().plusMinutes(10))));

        assertThatThrownBy(() -> service.findSession(10L, 99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_ACCESS_DENIED));
    }

    @Test
    void reportsConfirmedChecklistItemsOfSession() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        List<ListingChecklistItem> items =
                List.of(checklistItem(200L, "BODY"), checklistItem(201L, "SCREEN"));
        List<RtcSessionChecklistResult> results =
                List.of(checklistResult(200L, true, "이상 없음"));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(checklistRepository.findByListingIdOrderByDisplayOrderAsc(100L)).thenReturn(items);
        when(resultRepository.findByRtcSessionId(10L)).thenReturn(results);

        var response = service.findSession(10L, 20L);

        assertThat(response.sessionId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(RtcSessionStatus.WAITING.name());
        assertThat(response.checklistItems()).hasSize(2);
        assertThat(response.checklistItems().get(0).confirmed()).isTrue();
        assertThat(response.checklistItems().get(0).note()).isEqualTo("이상 없음");
        assertThat(response.checklistItems().get(1).confirmed()).isFalse();
        assertThat(response.checklistItems().get(1).note()).isNull();
    }

    @Test
    void marksSessionConnectedWithDefaultPeerToPeerType() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        stubJoinableSession(session, LocalDateTime.now().minusMinutes(1));

        var response = service.connected(10L, 20L, new MarkRtcConnectedRequest(null));

        assertThat(response.status()).isEqualTo(RtcSessionStatus.CONNECTED.name());
        assertThat(response.connectedAt()).isNotNull();
    }

    @Test
    void rejectsUnknownConnectionType() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        stubJoinableSession(session, LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(
                        () -> service.connected(10L, 20L, new MarkRtcConnectedRequest("SFU")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_INVALID_STATE));
    }

    @Test
    void expiresSessionWithoutExpirationTimestampOnJoin() {
        RtcSession session = session(10L, 1L, null);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.join(10L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_EXPIRED));
        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.EXPIRED);
    }

    @Test
    void rejectsJoinWhenAppointmentIsGone() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(10L, 20L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_NOT_FOUND));
    }

    @Test
    void issuesJoinTicketWithStunServerOnly() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        stubJoinableSession(session, LocalDateTime.now().minusMinutes(1));

        var response = service.join(10L, 30L);

        assertThat(response.sessionId()).isEqualTo(10L);
        assertThat(response.signalingUrl()).isEqualTo("/ws/rtc");
        assertThat(response.joinToken()).isNotBlank();
        assertThat(response.offerer()).isTrue();
        assertThat(response.iceServers()).hasSize(1);
        assertThat(response.iceServers().get(0).urls()).isEqualTo("stun:example.test:3478");
        assertThat(response.iceServers().get(0).username()).isNull();
    }

    @Test
    void addsTurnServerWhenTurnUrlIsConfigured() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        stubJoinableSession(session, LocalDateTime.now().minusMinutes(1));

        var response =
                service("turn:turn.example.test:3478", "turn-user", "turn-secret")
                        .join(10L, 20L);

        assertThat(response.offerer()).isFalse();
        assertThat(response.iceServers()).hasSize(2);
        assertThat(response.iceServers().get(1).urls())
                .isEqualTo("turn:turn.example.test:3478");
        assertThat(response.iceServers().get(1).username()).isEqualTo("turn-user");
        assertThat(response.iceServers().get(1).credential()).isEqualTo("turn-secret");
    }

    @Test
    void rejectsEndForExpiredSession() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().minusMinutes(1));
        session.expireIfDue(LocalDateTime.now());
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(
                        () ->
                                service.end(
                                        10L,
                                        20L,
                                        new EndRtcSessionRequest("COMPLETED", null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.RTC_SESSION_EXPIRED));
    }

    @Test
    void returnsCurrentStateWhenSessionIsAlreadyEnded() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        session.end(com.c203.limit.domain.rtc.domain.RtcEndReason.COMPLETED, "완료");
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(resultRepository.findByRtcSessionId(10L)).thenReturn(List.of());
        when(checklistRepository.findByListingIdOrderByDisplayOrderAsc(100L))
                .thenReturn(List.of());

        var response =
                service.end(10L, 20L, new EndRtcSessionRequest("COMPLETED", null, null));

        assertThat(response.status()).isEqualTo(RtcSessionStatus.ENDED.name());
        assertThat(response.memo()).isEqualTo("완료");
        verify(resultRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsChecklistItemThatDoesNotBelongToListing() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        List<ListingChecklistItem> items = List.of(checklistItem(200L, "BODY"));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(checklistRepository.findByListingIdOrderByDisplayOrderAsc(100L)).thenReturn(items);

        assertThatThrownBy(
                        () ->
                                service.end(
                                        10L,
                                        20L,
                                        new EndRtcSessionRequest(
                                                "COMPLETED",
                                                null,
                                                List.of(
                                                        new RtcChecklistResultRequest(
                                                                999L, true, null)))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(resultRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsUnknownEndReason() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(checklistRepository.findByListingIdOrderByDisplayOrderAsc(100L))
                .thenReturn(List.of());
        when(resultRepository.findByRtcSessionId(10L)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.end(
                                        10L,
                                        20L,
                                        new EndRtcSessionRequest("ABORTED", null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void savesChecklistResultsAndCompletesSessionAndAppointment() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        RtcSession session = session(10L, 1L, expiresAt);
        List<ListingChecklistItem> items = List.of(checklistItem(200L, "BODY"));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(checklistRepository.findByListingIdOrderByDisplayOrderAsc(100L)).thenReturn(items);
        when(resultRepository.findByRtcSessionId(10L)).thenReturn(List.of());

        var response =
                service.end(
                        10L,
                        20L,
                        new EndRtcSessionRequest(
                                "COMPLETED",
                                "확인 완료",
                                List.of(new RtcChecklistResultRequest(200L, true, "이상 없음"))));

        assertThat(response.status()).isEqualTo(RtcSessionStatus.ENDED.name());
        assertThat(response.memo()).isEqualTo("확인 완료");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.inspectionSubmittedAt()).isNotNull();
        assertThat(appointmentRepository.findById(1L).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
        verify(resultRepository).saveAll(anyList());
    }

    @Test
    void doesNotOverwriteChecklistResultsRecordedByCounterpart() {
        RtcSession session = session(10L, 1L, LocalDateTime.now().plusMinutes(10));
        List<ListingChecklistItem> items = List.of(checklistItem(200L, "BODY"));
        List<RtcSessionChecklistResult> results =
                List.of(checklistResult(200L, true, "먼저 기록됨"));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(checklistRepository.findByListingIdOrderByDisplayOrderAsc(100L)).thenReturn(items);
        when(resultRepository.findByRtcSessionId(10L)).thenReturn(results);

        service.end(
                10L,
                30L,
                new EndRtcSessionRequest(
                        "COMPLETED",
                        null,
                        List.of(new RtcChecklistResultRequest(200L, false, "재확인"))));

        verify(resultRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsResponseFromNonParticipant() {
        CallAppointment proposed =
                appointment(1L, AppointmentStatus.PROPOSED, LocalDateTime.now().plusMinutes(10));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(proposed));

        assertThatThrownBy(
                        () ->
                                service.respond(
                                        1L,
                                        99L,
                                        new com.c203.limit.domain.rtc.dto.request
                                                .RespondCallRequest(true, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    @Test
    void respondentRejectsProposedCallWithoutCreatingSession() {
        CallAppointment proposed =
                appointment(1L, AppointmentStatus.PROPOSED, LocalDateTime.now().plusMinutes(10));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(proposed));

        var response =
                service.respond(
                        1L,
                        30L,
                        new com.c203.limit.domain.rtc.dto.request.RespondCallRequest(
                                false, "일정 불가"));

        assertThat(response.status()).isEqualTo(AppointmentStatus.REJECTED.name());
        assertThat(response.rtcSessionId()).isNull();
        verify(sessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsWaitingSessionWhenRespondentAcceptsCall() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(10);
        CallAppointment proposed = appointment(1L, AppointmentStatus.PROPOSED, scheduledAt);
        ChatRoom room = ChatRoom.create(100L, 20L, 30L);
        ReflectionTestUtils.setField(room, "id", 10L);
        RtcSession created = session(50L, 1L, scheduledAt.plusMinutes(30));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(proposed));
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(sessionRepository.findByCallAppointmentId(1L)).thenReturn(Optional.empty());
        when(sessionRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(created);

        var response =
                service.respond(
                        1L,
                        30L,
                        new com.c203.limit.domain.rtc.dto.request.RespondCallRequest(true, null));

        assertThat(response.status()).isEqualTo(AppointmentStatus.ACCEPTED.name());
        assertThat(response.rtcSessionId()).isEqualTo(50L);
        assertThat(response.sessionExpiresAt()).isEqualTo(created.getExpiresAt());
    }

    @Test
    void rejectsAcceptanceWhenChatRoomIsMissing() {
        CallAppointment proposed =
                appointment(1L, AppointmentStatus.PROPOSED, LocalDateTime.now().plusMinutes(10));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(proposed));
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.respond(
                                        1L,
                                        30L,
                                        new com.c203.limit.domain.rtc.dto.request
                                                .RespondCallRequest(true, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    }

    private void stubJoinableSession(RtcSession session, LocalDateTime scheduledAt) {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(appointmentRepository.findById(session.getCallAppointmentId()))
                .thenReturn(Optional.of(appointment(1L, AppointmentStatus.ACCEPTED, scheduledAt)));
    }

    private RtcSession session(Long id, Long appointmentId, LocalDateTime expiresAt) {
        RtcSession session = RtcSession.waiting(appointmentId, 10L, 100L, 30L, 20L, expiresAt);
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private Member member(String nickname) {
        return Member.createLocal("user@example.com", "encoded", nickname, null);
    }

    private ListingChecklistItem checklistItem(Long id, String itemCode) {
        ListingChecklistItem item = mock(ListingChecklistItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getItemCode()).thenReturn(itemCode);
        when(item.getName()).thenReturn(itemCode + " 확인");
        when(item.getCaptureGuide()).thenReturn("가이드");
        return item;
    }

    private RtcSessionChecklistResult checklistResult(Long itemId, boolean confirmed, String note) {
        RtcSessionChecklistResult result = mock(RtcSessionChecklistResult.class);
        when(result.getListingChecklistItemId()).thenReturn(itemId);
        when(result.isConfirmed()).thenReturn(confirmed);
        when(result.getNote()).thenReturn(note);
        return result;
    }
}
