package com.c203.limit.domain.chat.repository;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRoomContextReader {
    private final JdbcClient jdbcClient;

    public ChatRoomContextReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Map<Long, ChatRoomContext> findAll(Collection<Long> roomIds, Long memberId) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql("""
                        SELECT room.id AS room_id,
                               counterpart.nickname AS counterpart_nickname,
                               listing.title AS listing_title,
                               (SELECT image.cdn_url
                                  FROM listing_image image
                                 WHERE image.listing_id = listing.id
                                   AND image.image_type = 'THUMBNAIL'
                                 ORDER BY image.id
                                 LIMIT 1) AS listing_thumbnail_url
                          FROM chat_room room
                          JOIN listing ON listing.id = room.listing_id
                          JOIN user_account counterpart
                            ON counterpart.user_id = CASE
                                 WHEN room.buyer_id = :memberId THEN room.seller_id
                                 ELSE room.buyer_id
                               END
                         WHERE room.id IN (:roomIds)
                        """)
                .param("memberId", memberId)
                .param("roomIds", roomIds)
                .query((resultSet, rowNum) -> new ChatRoomContext(
                        resultSet.getLong("room_id"),
                        resultSet.getString("counterpart_nickname"),
                        resultSet.getString("listing_title"),
                        resultSet.getString("listing_thumbnail_url")))
                .list()
                .stream()
                .collect(Collectors.toMap(ChatRoomContext::roomId, Function.identity()));
    }

    public record ChatRoomContext(
            Long roomId,
            String counterpartNickname,
            String listingTitle,
            String listingThumbnailUrl) {}
}
