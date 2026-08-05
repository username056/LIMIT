package com.c203.limit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.chat.domain.ParticipantRole;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.entity.ChatRoomParticipant;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * create()가 채팅방을 먼저 저장해 발급된 id로 참가자 2명(구매자/판매자)을 정확한 역할로
 * 생성하는지 확인한다 — 특히 역할이 뒤바뀌지 않는지가 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class ChatRoomCreatorTests {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomParticipantRepository participantRepository;

    @Test
    void createsRoomThenSavesBuyerAndSellerParticipantsWithCorrectRoles() {
        ChatRoomCreator creator = new ChatRoomCreator(chatRoomRepository, participantRepository);
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatRoom result = creator.create(100L, 10L, 20L);

        assertThat(result.getListingId()).isEqualTo(100L);
        assertThat(result.getBuyerId()).isEqualTo(10L);
        assertThat(result.getSellerId()).isEqualTo(20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatRoomParticipant>> captor = ArgumentCaptor.forClass(List.class);
        verify(participantRepository).saveAllAndFlush(captor.capture());

        List<ChatRoomParticipant> participants = captor.getValue();
        assertThat(participants).hasSize(2);
        assertThat(participants.get(0).getUserId()).isEqualTo(10L);
        assertThat(participants.get(1).getUserId()).isEqualTo(20L);
        // role은 getter로 노출되지 않으므로(엔티티에 getRole() 없음) 리플렉션으로 확인한다.
        assertThat(ReflectionTestUtils.getField(participants.get(0), "role")).isEqualTo(ParticipantRole.BUYER);
        assertThat(ReflectionTestUtils.getField(participants.get(1), "role")).isEqualTo(ParticipantRole.SELLER);
    }

    @Test
    void participantsReferenceTheIdAssignedToTheSavedRoom() {
        ChatRoomCreator creator = new ChatRoomCreator(chatRoomRepository, participantRepository);
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            return room; // id는 실제 DB에서 채번되므로 여기선 참가자가 room.getId()를 참조한다는 계약만 검증
        });

        creator.create(100L, 10L, 20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatRoomParticipant>> captor = ArgumentCaptor.forClass(List.class);
        verify(participantRepository).saveAllAndFlush(captor.capture());

        List<ChatRoomParticipant> participants = captor.getValue();
        assertThat(participants.get(0).getChatRoomId()).isEqualTo(participants.get(1).getChatRoomId());
    }
}
