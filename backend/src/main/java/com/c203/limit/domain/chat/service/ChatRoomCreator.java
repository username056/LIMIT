package com.c203.limit.domain.chat.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.c203.limit.domain.chat.domain.ParticipantRole;
import com.c203.limit.domain.chat.entity.ChatRoom;
import com.c203.limit.domain.chat.entity.ChatRoomParticipant;
import com.c203.limit.domain.chat.repository.ChatRoomParticipantRepository;
import com.c203.limit.domain.chat.repository.ChatRoomRepository;

@Component
public class ChatRoomCreator {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository participantRepository;

    public ChatRoomCreator(ChatRoomRepository chatRoomRepository,
            ChatRoomParticipantRepository participantRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChatRoom create(Long listingId, Long buyerId, Long sellerId) {
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.create(listingId, buyerId, sellerId));
        participantRepository.saveAllAndFlush(List.of(
                ChatRoomParticipant.create(room.getId(), buyerId, ParticipantRole.BUYER),
                ChatRoomParticipant.create(room.getId(), sellerId, ParticipantRole.SELLER)));
        return room;
    }
}
