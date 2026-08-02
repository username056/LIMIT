package com.c203.limit.domain.payment.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 주문 내역 화면에 필요한 매물 표시 정보(상품명·상태·대표 이미지)를 조회한다. product 도메인의
 * Entity/Repository를 직접 참조하지 않도록, {@code ListingChatReader}와 같은 방식으로 listing,
 * listing_image 테이블을 직접 읽는다.
 */
@Repository
public class ListingOrderSummaryReader {
    private final JdbcClient jdbcClient;

    public ListingOrderSummaryReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<ListingOrderSummary> findByIds(List<Long> listingIds) {
        if (listingIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT l.id AS listing_id, l.title AS title, l.status AS status,
                               li.cdn_url AS cdn_url, li.s3_key AS s3_key
                        FROM listing l
                        LEFT JOIN listing_image li
                               ON li.listing_id = l.id
                              AND li.image_type = 'THUMBNAIL'
                              AND li.id = (
                                    SELECT MIN(candidate.id)
                                    FROM listing_image candidate
                                    WHERE candidate.listing_id = l.id
                                      AND candidate.image_type = 'THUMBNAIL'
                              )
                        WHERE l.id IN (:listingIds)
                        """)
                .param("listingIds", listingIds)
                .query((rs, rowNum) -> new ListingOrderSummary(
                        rs.getLong("listing_id"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getString("cdn_url"),
                        rs.getString("s3_key")))
                .list();
    }

    public record ListingOrderSummary(
            Long listingId, String title, String status, String cdnUrl, String s3Key) {}
}
