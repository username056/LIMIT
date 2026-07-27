package com.c203.limit.domain.rtc.service;

import com.c203.limit.domain.call.domain.AppointmentStatus;
import com.c203.limit.domain.call.entity.CallAppointment;
import com.c203.limit.domain.call.repository.CallAppointmentRepository;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.rtc.domain.ConnectionType;
import com.c203.limit.domain.rtc.domain.RtcEndReason;
import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import com.c203.limit.domain.rtc.dto.request.CreateCallRequest;
import com.c203.limit.domain.rtc.dto.request.EndRtcSessionRequest;
import com.c203.limit.domain.rtc.dto.request.MarkRtcConnectedRequest;
import com.c203.limit.domain.rtc.dto.request.RespondCallRequest;
import com.c203.limit.domain.rtc.dto.response.CallResponse;
import com.c203.limit.domain.rtc.dto.response.RtcChecklistItemResponse;
import com.c203.limit.domain.rtc.dto.response.RtcJoinResponse;
import com.c203.limit.domain.rtc.dto.response.RtcJoinResponse.IceServerResponse;
import com.c203.limit.domain.rtc.dto.response.RtcSessionResponse;
import com.c203.limit.domain.rtc.entity.RtcSession;
import com.c203.limit.domain.rtc.entity.RtcSessionChecklistResult;
import com.c203.limit.domain.rtc.repository.RtcSessionChecklistResultRepository;
import com.c203.limit.domain.rtc.repository.RtcSessionRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RtcCallService {
    private static final Logger log = LoggerFactory.getLogger(RtcCallService.class);

    private final CallAppointmentRepository appointmentRepository;
    private final RtcSessionRepository sessionRepository;
    private final RtcSessionChecklistResultRepository resultRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ListingChecklistItemRepository checklistRepository;
    private final RtcJoinTokenStore tokenStore;
    private final String stunUrl;
    private final String turnUrl;
    private final String turnUsername;
    private final String turnCredential;

    public RtcCallService(
            CallAppointmentRepository appointmentRepository,
            RtcSessionRepository sessionRepository,
            RtcSessionChecklistResultRepository resultRepository,
            ChatRoomRepository chatRoomRepository,
            ListingChecklistItemRepository checklistRepository,
            RtcJoinTokenStore tokenStore,
            @Value("${limit.rtc.stun-url:stun:stun.l.google.com:19302}") String stunUrl,
            @Value("${limit.rtc.turn-url:}") String turnUrl,
            @Value("${limit.rtc.turn-username:}") String turnUsername,
            @Value("${limit.rtc.turn-credential:}") String turnCredential) {
        this.appointmentRepository = appointmentRepository;
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.checklistRepository = checklistRepository;
        this.tokenStore = tokenStore;
        this.stunUrl = stunUrl;
        this.turnUrl = turnUrl;
        this.turnUsername = turnUsername;
        this.turnCredential = turnCredential;
    }

    @Transactional
    public CallResponse request(Long roomId, Long memberId, CreateCallRequest request) {
        ChatRoom room = room(roomId, memberId);
        Long respondent =
                room.getBuyerId().equals(memberId) ? room.getSellerId() : room.getBuyerId();
        LocalDateTime scheduledAt =
                request.scheduledAt() == null ? LocalDateTime.now() : request.scheduledAt();
        CallAppointment appointment =
                appointmentRepository.save(
                        CallAppointment.propose(
                                roomId, memberId, respondent, scheduledAt, request.memo()));
        log.info(
                "RTC inspection call requested: callId={}, chatRoomId={}",
                appointment.getId(),
                roomId);
        return callResponse(appointment, null, memberId);
    }

    @Transactional(readOnly = true)
    public List<CallResponse> findMine(Long memberId) {
        List<CallAppointment> appointments = appointmentRepository.findMine(memberId);
        if (appointments.isEmpty()) {
            return List.of();
        }
        Map<Long, RtcSession> sessions =
                sessionRepository
                        .findByCallAppointmentIdIn(
                                appointments.stream().map(CallAppointment::getId).toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        RtcSession::getCallAppointmentId, Function.identity()));
        return appointments.stream()
                .map(a -> callResponse(a, sessions.get(a.getId()), memberId))
                .toList();
    }

    @Transactional(readOnly = true)
    public CallResponse findCall(Long callId, Long memberId) {
        CallAppointment appointment = appointment(callId, memberId);
        return callResponse(
                appointment,
                sessionRepository.findByCallAppointmentId(callId).orElse(null),
                memberId);
    }

    @Transactional
    public CallResponse respond(Long callId, Long memberId, RespondCallRequest request) {
        CallAppointment appointment = appointment(callId, memberId);
        try {
            if (!request.accepted()) {
                appointment.reject(memberId, request.reason());
                return callResponse(appointment, null, memberId);
            }
            appointment.accept(memberId);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.RTC_INVALID_STATE);
        }
        ChatRoom room = room(appointment.getChatRoomId(), memberId);
        RtcSession session =
                sessionRepository
                        .findByCallAppointmentId(callId)
                        .orElseGet(
                                () ->
                                        sessionRepository.save(
                                                RtcSession.waiting(
                                                        callId,
                                                        room.getId(),
                                                        room.getListingId(),
                                                        room.getSellerId(),
                                                        room.getBuyerId(),
                                                        LocalDateTime.now().plusHours(2))));
        return callResponse(appointment, session, memberId);
    }

    @Transactional(readOnly = true)
    public RtcSessionResponse findSession(Long sessionId, Long memberId) {
        return sessionResponse(session(sessionId, memberId));
    }

    @Transactional
    public RtcJoinResponse join(Long sessionId, Long memberId) {
        RtcSession session = session(sessionId, memberId);
        ensureJoinable(session, LocalDateTime.now());
        var ticket = tokenStore.issue(sessionId, memberId);
        return new RtcJoinResponse(
                sessionId,
                "/ws/rtc",
                ticket.token(),
                ticket.expiresAt(),
                session.getSellerId().equals(memberId),
                iceServers());
    }

    @Transactional
    public RtcSessionResponse connected(
            Long sessionId, Long memberId, MarkRtcConnectedRequest request) {
        RtcSession session = session(sessionId, memberId);
        ensureJoinable(session, LocalDateTime.now());
        try {
            session.connect(
                    ConnectionType.valueOf(
                            request.connectionType() == null ? "P2P" : request.connectionType()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new BusinessException(ErrorCode.RTC_INVALID_STATE);
        }
        return sessionResponse(session);
    }

    @Transactional
    public RtcSessionResponse end(Long sessionId, Long memberId, EndRtcSessionRequest request) {
        RtcSession session = session(sessionId, memberId);
        if (session.getStatus() == RtcSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.RTC_SESSION_EXPIRED);
        }
        if (session.getStatus() == RtcSessionStatus.ENDED) {
            return sessionResponse(session);
        }
        List<com.c203.limit.domain.rtc.dto.request.RtcChecklistResultRequest> requested =
                request.checklistResults() == null ? List.of() : request.checklistResults();
        Set<Long> requestedItemIds = new HashSet<>();
        if (requested.stream()
                .map(item -> item.checklistItemId())
                .anyMatch(itemId -> !requestedItemIds.add(itemId))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Map<Long, ListingChecklistItem> validItems =
                checklistRepository
                        .findByListingIdOrderByDisplayOrderAsc(session.getListingId())
                        .stream()
                        .collect(
                                Collectors.toMap(ListingChecklistItem::getId, Function.identity()));
        if (requested.stream().anyMatch(item -> !validItems.containsKey(item.checklistItemId()))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (resultRepository.findByRtcSessionId(sessionId).isEmpty()) {
            resultRepository.saveAll(
                    requested.stream()
                            .map(
                                    item ->
                                            RtcSessionChecklistResult.record(
                                                    sessionId,
                                                    item.checklistItemId(),
                                                    memberId,
                                                    item.confirmed(),
                                                    item.note()))
                            .toList());
        }
        RtcEndReason reason;
        try {
            reason = RtcEndReason.valueOf(request.endReason());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        session.end(reason, request.memo());
        log.info("RTC inspection session ended: sessionId={}, reason={}", sessionId, reason);
        appointmentRepository
                .findById(session.getCallAppointmentId())
                .ifPresent(
                        appointment -> {
                            if (appointment.getStatus() == AppointmentStatus.ACCEPTED)
                                appointment.complete();
                        });
        return sessionResponse(session);
    }

    private ChatRoom room(Long roomId, Long memberId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        if (!room.getBuyerId().equals(memberId) && !room.getSellerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
        return room;
    }

    private CallAppointment appointment(Long callId, Long memberId) {
        CallAppointment appointment =
                appointmentRepository
                        .findById(callId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        if (!appointment.isParticipant(memberId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
        return appointment;
    }

    private RtcSession session(Long sessionId, Long memberId) {
        RtcSession session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RTC_SESSION_NOT_FOUND));
        if (!session.isParticipant(memberId)) {
            throw new BusinessException(ErrorCode.RTC_SESSION_ACCESS_DENIED);
        }
        return session;
    }

    private void ensureJoinable(RtcSession session, LocalDateTime now) {
        if (session.getStatus() == RtcSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.RTC_SESSION_EXPIRED);
        }
        if (session.getStatus() == RtcSessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.RTC_SESSION_CLOSED);
        }
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            session.expireIfDue(now);
            throw new BusinessException(ErrorCode.RTC_SESSION_EXPIRED);
        }
    }

    private CallResponse callResponse(
            CallAppointment appointment, RtcSession session, Long memberId) {
        return new CallResponse(
                appointment.getId(),
                appointment.getChatRoomId(),
                appointment.getProposerId(),
                appointment.getRespondentId(),
                appointment.getStatus().name(),
                appointment.getScheduledAt(),
                appointment.getMemo(),
                appointment.getCancelReason(),
                session == null ? null : session.getId(),
                appointment.getRespondentId().equals(memberId));
    }

    private RtcSessionResponse sessionResponse(RtcSession session) {
        Map<Long, RtcSessionChecklistResult> results =
                resultRepository.findByRtcSessionId(session.getId()).stream()
                        .collect(
                                Collectors.toMap(
                                        RtcSessionChecklistResult::getListingChecklistItemId,
                                        Function.identity()));
        List<RtcChecklistItemResponse> checklistItems =
                checklistRepository
                        .findByListingIdOrderByDisplayOrderAsc(session.getListingId())
                        .stream()
                        .map(
                                item -> {
                                    RtcSessionChecklistResult result = results.get(item.getId());
                                    return new RtcChecklistItemResponse(
                                            item.getId(),
                                            item.getItemCode(),
                                            item.getName(),
                                            item.getCaptureGuide(),
                                            result != null && result.isConfirmed(),
                                            result == null ? null : result.getNote());
                                })
                        .toList();
        return new RtcSessionResponse(
                session.getId(),
                session.getCallAppointmentId(),
                session.getChatRoomId(),
                session.getListingId(),
                session.getSellerId(),
                session.getBuyerId(),
                session.getStatus().name(),
                session.getExpiresAt(),
                session.getConnectedAt(),
                session.getEndedAt(),
                session.getVerificationMemo(),
                checklistItems);
    }

    private List<IceServerResponse> iceServers() {
        List<IceServerResponse> servers = new java.util.ArrayList<>();
        servers.add(new IceServerResponse(stunUrl, null, null));
        if (turnUrl != null && !turnUrl.isBlank()) {
            servers.add(new IceServerResponse(turnUrl, turnUsername, turnCredential));
        }
        return List.copyOf(servers);
    }
}
