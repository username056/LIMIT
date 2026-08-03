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
                                 LIMIT 1) AS listing_thumbnail_url,
                               last_message.message_type AS last_message_type,
                               last_message.content AS last_message_content
                          FROM chat_room room
                          JOIN listing ON listing.id = room.listing_id
                          JOIN user_account counterpart
                            ON counterpart.user_id = CASE
                                 WHEN room.buyer_id = :memberId THEN room.seller_id
                                 ELSE room.buyer_id
                               END
                          LEFT JOIN chat_message last_message
                            ON last_message.id = (
                                 SELECT message.id
                                   FROM chat_message message
                                  WHERE message.chat_room_id = room.id
                                    AND message.deleted_at IS NULL
                                  ORDER BY message.room_sequence DESC
                                  LIMIT 1)
                         WHERE room.id IN (:roomIds)
                        """)
                .param("memberId", memberId)
                .param("roomIds", roomIds)
                .query((resultSet, rowNum) -> new ChatRoomContext(
                        resultSet.getLong("room_id"),
                        resultSet.getString("counterpart_nickname"),
                        resultSet.getString("listing_title"),
                        resultSet.getString("listing_thumbnail_url"),
                        lastMessagePreview(
                                resultSet.getString("last_message_type"),
                                resultSet.getString("last_message_content"))))
                .list()
                .stream()
                .collect(Collectors.toMap(ChatRoomContext::roomId, Function.identity()));
    }

    /**
     * 목록에 한 줄로 보여 줄 마지막 메시지.
     *
     * <p>사진·영상은 content에 파일명이 들어 있어 그대로 내보내면 목록에
     * "IMG_2381.jpeg"가 뜹니다. 무엇을 보냈는지만 알립니다.
     *
     * <p>본문은 잘라서 내보냅니다. content가 LONGTEXT라, 긴 글을 보낸 방이
     * 목록에 섞이면 응답 크기가 방 수만큼 불어납니다.
     */
    static String lastMessagePreview(String messageType, String content) {
        if (messageType == null) {
            return null;
        }
        if ("IMAGE".equals(messageType)) {
            return "사진을 보냈습니다.";
        }
        if ("VIDEO".equals(messageType)) {
            return "영상을 보냈습니다.";
        }
        if (content == null) {
            return null;
        }
        String singleLine = content.replaceAll("\\s+", " ").trim();
        if (singleLine.isEmpty()) {
            return null;
        }
        return singleLine.length() > PREVIEW_MAX_LENGTH
                ? singleLine.substring(0, PREVIEW_MAX_LENGTH) + "…"
                : singleLine;
    }

    private static final int PREVIEW_MAX_LENGTH = 100;

    public record ChatRoomContext(
            Long roomId,
            String counterpartNickname,
            String listingTitle,
            String listingThumbnailUrl,
            String lastMessagePreview) {}
}
