package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.call.event.CallAppointmentNotificationAction;
import com.c203.limit.domain.call.event.CallAppointmentUpdatedNotificationEvent;
import com.c203.limit.domain.chat.entity.ChatMessage;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.repository.ChatMessageRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CallAppointmentChatNotificationServiceTests {

    @ParameterizedTest
    @MethodSource("appointmentNotifications")
    void savesAppointmentActionAsNicknameDividerMessage(
            CallAppointmentNotificationAction action, String expectedContent) {
        ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
        ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
        CallAppointmentChatNotificationService service =
                new CallAppointmentChatNotificationService(roomRepository, messageRepository);
        ChatRoom room = ChatRoom.create(100L, 20L, 30L);
        UUID eventId = UUID.randomUUID();
        var event =
                new CallAppointmentUpdatedNotificationEvent(
                        eventId,
                        1L,
                        10L,
                        20L,
                        "민수",
                        action,
                        LocalDateTime.of(2026, 8, 1, 15, 30),
                        null);
        when(messageRepository.findByChatRoomIdAndClientMessageId(10L, eventId))
                .thenReturn(Optional.empty());
        when(roomRepository.findLockedById(10L)).thenReturn(Optional.of(room));
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.save(event);

        assertThat(result.created()).isTrue();
        assertThat(result.message().content()).isEqualTo(expectedContent);
        assertThat(result.message().type()).isEqualTo("SYSTEM");
    }

    private static Stream<Arguments> appointmentNotifications() {
        return Stream.of(
                Arguments.of(
                        CallAppointmentNotificationAction.CREATED,
                        "민수 님이 실시간 검증 약속을 설정했습니다."),
                Arguments.of(
                        CallAppointmentNotificationAction.UPDATED,
                        "민수 님이 실시간 검증 약속을 변경했습니다."),
                Arguments.of(
                        CallAppointmentNotificationAction.CANCELED,
                        "민수 님이 실시간 검증 약속을 취소했습니다."));
    }
}
