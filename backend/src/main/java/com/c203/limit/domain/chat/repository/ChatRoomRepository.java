package com.c203.limit.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.c203.limit.domain.chat.entity.ChatRoom;
import jakarta.persistence.LockModeType;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByListingIdAndBuyerIdAndSellerId(
            Long listingId, Long buyerId, Long sellerId);

    Optional<ChatRoom> findFirstByBuyerIdAndSellerIdOrderByIdDesc(
            Long buyerId, Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT room FROM ChatRoom room WHERE room.id = :roomId")
    Optional<ChatRoom> findLockedById(@Param("roomId") Long roomId);

    @Query("""
            SELECT room.id AS roomId,
                   room.listingId AS listingId,
                   room.buyerId AS buyerId,
                   room.sellerId AS sellerId,
                   room.status AS status,
                   room.lastMessageId AS lastMessageId,
                   room.lastMessageSeq AS lastMessageSeq,
                   room.lastMessageAt AS lastMessageAt,
                   participant.lastReadSeq AS lastReadSeq,
                   counterpart.lastReadSeq AS counterpartLastReadSeq,
                   room.createdAt AS createdAt
              FROM ChatRoom room
              JOIN ChatRoomParticipant participant ON participant.chatRoomId = room.id
              JOIN ChatRoomParticipant counterpart ON counterpart.chatRoomId = room.id
             WHERE participant.userId = :memberId
               AND participant.leftAt IS NULL
               AND ((room.buyerId = :memberId AND counterpart.userId = room.sellerId)
                    OR (room.sellerId = :memberId AND counterpart.userId = room.buyerId))
               AND counterpart.leftAt IS NULL
               AND (:cursor IS NULL OR room.id < :cursor)
             ORDER BY room.id DESC
            """)
    List<ChatRoomSummaryProjection> findSummariesByMemberId(
            @Param("memberId") Long memberId,
            @Param("cursor") Long cursor,
            Pageable pageable);
}
