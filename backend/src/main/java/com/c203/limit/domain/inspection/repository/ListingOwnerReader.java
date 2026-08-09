package com.c203.limit.domain.inspection.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 매물(listing) 소유자를 조회하기 위한 도메인 간 읽기 전용 리더. product 도메인의 Listing 엔티티를 직접
 * 참조하지 않고 필요한 컬럼만 조회한다(chat 도메인의 ListingChatReader와 동일한 패턴).
 */
@Repository
public class ListingOwnerReader {

    private final JdbcClient jdbcClient;

    public ListingOwnerReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<ListingOwnerInfo> findById(Long listingId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, seller_id
                        FROM listing
                        WHERE id = :listingId AND deleted_at IS NULL
                        """)
                .param("listingId", listingId)
                .query(
                        (resultSet, rowNum) ->
                                new ListingOwnerInfo(
                                        resultSet.getLong("id"), resultSet.getLong("seller_id")))
                .optional();
    }

    public Optional<ListingVisibilityInfo> findVisibilityById(Long listingId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, seller_id, status, moderation_status
                        FROM listing
                        WHERE id = :listingId AND deleted_at IS NULL
                        """)
                .param("listingId", listingId)
                .query((resultSet, rowNum) -> new ListingVisibilityInfo(
                        resultSet.getLong("id"),
                        resultSet.getLong("seller_id"),
                        resultSet.getString("status"),
                        resultSet.getString("moderation_status")))
                .optional();
    }

    public record ListingOwnerInfo(Long listingId, Long sellerId) {}

    public record ListingVisibilityInfo(
            Long listingId, Long sellerId, String status, String moderationStatus) {
        public boolean publiclyVisible() {
            return "ON_SALE".equals(status)
                    && ("NORMAL".equals(moderationStatus)
                            || "WARNING_ACK_REQUIRED".equals(moderationStatus));
        }
    }
}
