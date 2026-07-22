package com.c203.limit.domain.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.c203.limit.domain.chat.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByListingIdAndBuyerIdAndSellerId(
            Long listingId, Long buyerId, Long sellerId);
}
