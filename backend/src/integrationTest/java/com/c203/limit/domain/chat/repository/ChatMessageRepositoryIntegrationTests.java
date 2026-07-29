package com.c203.limit.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.testsupport.AbstractMySqlIntegrationTest;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
@Transactional
class ChatMessageRepositoryIntegrationTests extends AbstractMySqlIntegrationTest {

    private static final long BUYER_ID = 91_001L;
    private static final long SELLER_ID = 91_002L;
    private static final long ROOM_ID = 92_001L;
    private static final long OTHER_ROOM_ID = 92_002L;

    @Autowired ChatMessageRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertMember(BUYER_ID, "chat-buyer@example.com", "chat-buyer");
        insertMember(SELLER_ID, "chat-seller@example.com", "chat-seller");
        insertRoom(ROOM_ID, 93_001L);
        insertRoom(OTHER_ROOM_ID, 93_002L);

        insertMessage(ROOM_ID, 1L, false);
        insertMessage(ROOM_ID, 2L, false);
        insertMessage(ROOM_ID, 3L, false);
        insertMessage(ROOM_ID, 4L, true);
        insertMessage(OTHER_ROOM_ID, 1L, false);
    }

    @Test
    void findsMessagesBeforeSequenceInDescendingOrder() {
        List<ChatMessageProjection> result =
                repository.findBeforeSequence(ROOM_ID, 4L, PageRequest.of(0, 10));

        assertThat(result)
                .extracting(ChatMessageProjection::getRoomSequence)
                .containsExactly(3L, 2L, 1L);
        assertThat(result)
                .extracting(ChatMessageProjection::getClientMessageId)
                .containsExactly(messageUuid(ROOM_ID, 3L), messageUuid(ROOM_ID, 2L), messageUuid(ROOM_ID, 1L));
    }

    @Test
    void findsMessagesAfterSequenceInAscendingOrder() {
        List<ChatMessageProjection> result =
                repository.findAfterSequence(ROOM_ID, 1L, PageRequest.of(0, 10));

        assertThat(result)
                .extracting(ChatMessageProjection::getRoomSequence)
                .containsExactly(2L, 3L);
    }

    private void insertMember(long memberId, String email, String nickname) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, email, password, nickname, status, marketing_opt_in, created_at, updated_at
                ) VALUES (?, ?, NULL, ?, 'ACTIVE', b'0', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                memberId,
                email,
                nickname);
    }

    private void insertRoom(long roomId, long listingId) {
        jdbcTemplate.update(
                """
                INSERT INTO chat_room (
                    id, listing_id, buyer_id, seller_id, status, last_message_seq, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', 4, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                roomId,
                listingId,
                BUYER_ID,
                SELLER_ID);
    }

    private void insertMessage(long roomId, long sequence, boolean deleted) {
        jdbcTemplate.update(
                """
                INSERT INTO chat_message (
                    chat_room_id, room_sequence, sender_id, client_message_id,
                    message_type, content, status, sent_at, deleted_at
                ) VALUES (
                    ?, ?, ?, UNHEX(REPLACE(?, '-', '')),
                    'TEXT', ?, ?, CURRENT_TIMESTAMP(6),
                    CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE NULL END
                )
                """,
                roomId,
                sequence,
                SELLER_ID,
                messageUuid(roomId, sequence).toString(),
                "message-" + sequence,
                deleted ? "DELETED" : "SENT",
                deleted);
    }

    private UUID messageUuid(long roomId, long sequence) {
        return new UUID(roomId, sequence);
    }
}
