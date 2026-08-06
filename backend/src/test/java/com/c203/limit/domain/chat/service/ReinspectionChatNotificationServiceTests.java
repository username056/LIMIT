package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.chat.domain.OutboxStatus;
import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.entity.ChatOutboxEvent;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.entity.ReinspectionRequestMessage;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatOutboxEventRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import com.c203.limit.domain.chat.repository.ReinspectionRequestMessageRepository;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReinspectionChatNotificationServiceTests {
    private static final UUID EVENT_ID = new UUID(1L, 2L);
    private static final Long ROOM_ID = 20L;
    private static final Long REQUEST_ID = 10L;
    private static final Long ACTOR_ID = 30L;
    private static final Long RECIPIENT_ID = 40L;

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ReinspectionRequestMessageRepository requestMessageRepository;
    @Mock ChatOutboxEventRepository outboxEventRepository;

    ReinspectionChatNotificationService service;

    @BeforeEach
    void setUp() {
        service = new ReinspectionChatNotificationService(
                chatRoomRepository, chatMessageRepository, requestMessageRepository,
                outboxEventRepository, new ObjectMapper());
    }

    @Test
    void savesRequestedNotificationWithNextRoomSequenceAndOutboxEvent() {
        ChatRoom room = room();
        ReflectionTestUtils.setField(room, "lastMessageSeq", 4L);
        when(chatMessageRepository.findByChatRoomIdAndClientMessageId(ROOM_ID, EVENT_ID))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 501L);
            return message;
        });
        when(outboxEventRepository.save(any(ChatOutboxEvent.class)))
                .thenAnswer(invocation -> {
                    ChatOutboxEvent outboxEvent = invocation.getArgument(0);
                    ReflectionTestUtils.setField(outboxEvent, "id", 900L);
                    return outboxEvent;
                });

        var result = service.save(requestedEvent());

        assertThat(result.created()).isTrue();
        assertThat(result.outboxEventId()).isEqualTo(900L);
        assertThat(result.message().messageId()).isEqualTo(501L);
        assertThat(result.message().roomSequence()).isEqualTo(5L);
        assertThat(result.message().type()).isEqualTo("SYSTEM");
        assertThat(result.message().senderId()).isEqualTo(ACTOR_ID);
        assertThat(result.message().clientMessageId()).isEqualTo(EVENT_ID);
        assertThat(result.message().content())
                .contains("재검수 요청이 2건 들어왔어요!")
                .contains("사유: 흠집이 있어요")
                .contains("- 앞면: 다시 찍어 주세요")
                .contains("바로 재촬영하기");
        // 방의 마지막 메시지가 갱신되어야 다음 메시지가 같은 번호를 다시 쓰지 않는다.
        assertThat(room.nextMessageSequence()).isEqualTo(6L);
    }

    @Test
    void linksSavedMessageToReinspectionRequestAndPublishesOutboxPayload() {
        ChatRoom room = room();
        when(chatMessageRepository.findByChatRoomIdAndClientMessageId(ROOM_ID, EVENT_ID))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 501L);
            return message;
        });
        when(outboxEventRepository.save(any(ChatOutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.save(requestedEvent());

        verify(requestMessageRepository).save(any(ReinspectionRequestMessage.class));
        ArgumentCaptor<ChatOutboxEvent> captor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("REINSPECTION_REQUESTED");
        assertThat(captor.getValue().getPayload())
                .contains("\"requestKey\":\"request-key\"")
                .contains("\"pendingRequestCount\":2")
                .contains("START_RECAPTURE");
    }

    @Test
    void writesCompletionNoticeWithoutItemListForCompletedEvent() {
        ChatRoom room = room();
        when(chatMessageRepository.findByChatRoomIdAndClientMessageId(ROOM_ID, EVENT_ID))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.of(room));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 502L);
            return message;
        });
        when(outboxEventRepository.save(any(ChatOutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.save(event(ReinspectionNotificationEvent.Type.COMPLETED, List.of()));

        assertThat(result.message().content()).isEqualTo("재검수가 완료되었습니다.");
        ArgumentCaptor<ChatOutboxEvent> captor = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("REINSPECTION_COMPLETED");
        // 완료 알림에는 재촬영 버튼이 없다.
        assertThat(captor.getValue().getPayload()).contains("\"action\":null");
    }

    @Test
    void returnsAlreadySavedMessageWhenSameEventArrivesAgain() {
        ChatMessage existing = ChatMessage.sendSystem(
                ROOM_ID, 3L, ACTOR_ID, EVENT_ID, "재검수가 완료되었습니다.",
                LocalDateTime.of(2026, 7, 23, 12, 0));
        ReflectionTestUtils.setField(existing, "id", 300L);
        when(chatMessageRepository.findByChatRoomIdAndClientMessageId(ROOM_ID, EVENT_ID))
                .thenReturn(Optional.of(existing));

        var result = service.save(requestedEvent());

        assertThat(result.created()).isFalse();
        assertThat(result.outboxEventId()).isNull();
        assertThat(result.message().messageId()).isEqualTo(300L);
        verifyNoInteractions(chatRoomRepository, requestMessageRepository, outboxEventRepository);
    }

    @Test
    void rejectsNotificationWhenChatRoomIsMissing() {
        when(chatMessageRepository.findByChatRoomIdAndClientMessageId(ROOM_ID, EVENT_ID))
                .thenReturn(Optional.empty());
        when(chatRoomRepository.findLockedById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(requestedEvent()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REINSPECTION_CHAT_ROOM_NOT_FOUND));
        verifyNoInteractions(requestMessageRepository, outboxEventRepository);
    }

    @Test
    void marksOutboxEventPublished() {
        ChatOutboxEvent outboxEvent = ChatOutboxEvent.pending(
                EVENT_ID, "REINSPECTION_REQUEST", REQUEST_ID, "REINSPECTION_REQUESTED", "{}",
                LocalDateTime.of(2026, 7, 23, 12, 0));
        when(outboxEventRepository.findById(900L)).thenReturn(Optional.of(outboxEvent));

        service.markPublished(900L);

        assertThat(ReflectionTestUtils.getField(outboxEvent, "status"))
                .isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(ReflectionTestUtils.getField(outboxEvent, "publishedAt")).isNotNull();
    }

    @Test
    void ignoresPublishMarkWhenOutboxEventIdIsNull() {
        service.markPublished(null);

        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void ignoresPublishMarkWhenOutboxEventIsAlreadyGone() {
        when(outboxEventRepository.findById(900L)).thenReturn(Optional.empty());

        service.markPublished(900L);
    }

    @Test
    void usesMinimalOutboxPayloadWhenStructuredPayloadSerializationFails()
            throws JsonProcessingException {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") {});
        ReinspectionChatNotificationService service = new ReinspectionChatNotificationService(
                mock(ChatRoomRepository.class),
                mock(ChatMessageRepository.class),
                mock(ReinspectionRequestMessageRepository.class),
                mock(ChatOutboxEventRepository.class),
                objectMapper);
        UUID eventId = new UUID(1L, 2L);
        ReinspectionNotificationEvent event = new ReinspectionNotificationEvent(
                eventId,
                ReinspectionNotificationEvent.Type.REQUESTED,
                10L,
                "request-key",
                100L,
                20L,
                30L,
                40L,
                2,
                "사유",
                List.of());

        String payload = ReflectionTestUtils.invokeMethod(service, "serializePayload", event);

        assertThat(payload)
                .contains(eventId.toString())
                .contains("\"reinspectionRequestId\":10")
                .contains("\"serializationFallback\":true");
    }

    private ChatRoom room() {
        ChatRoom room = ChatRoom.create(100L, RECIPIENT_ID, ACTOR_ID);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private ReinspectionNotificationEvent requestedEvent() {
        return event(
                ReinspectionNotificationEvent.Type.REQUESTED,
                List.of(new ReinspectionNotificationEvent.Item("앞면", "다시 찍어 주세요")));
    }

    private ReinspectionNotificationEvent event(
            ReinspectionNotificationEvent.Type type,
            List<ReinspectionNotificationEvent.Item> items) {
        return new ReinspectionNotificationEvent(
                EVENT_ID, type, REQUEST_ID, "request-key", 100L, ROOM_ID, ACTOR_ID, RECIPIENT_ID,
                2, "흠집이 있어요", items);
    }
}
