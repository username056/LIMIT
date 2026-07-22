package com.c203.limit.domain.chat.repository;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ListingChatReader {
    private final JdbcClient jdbcClient;

    public ListingChatReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ListingChatInfo> findById(Long listingId) {
        return jdbcClient.sql("""
                        SELECT id, seller_id, status
                        FROM listing
                        WHERE id = :listingId AND deleted_at IS NULL
                        """)
                .param("listingId", listingId)
                .query((resultSet, rowNum) -> new ListingChatInfo(
                        resultSet.getLong("id"),
                        resultSet.getLong("seller_id"),
                        resultSet.getString("status")))
                .optional();
    }

    public record ListingChatInfo(Long listingId, Long sellerId, String status) {}
}
