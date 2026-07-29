package com.c203.limit.domain.payment.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 예약 유예 시간이 지난 매물 ID를 조회한다. product 도메인의 Entity/Repository를 직접 참조하지
 * 않도록, {@code ListingChatReader}와 같은 방식으로 listing 테이블을 직접 읽는다.
 */
@Repository
public class ExpiredReservationCandidateReader {
    private final JdbcClient jdbcClient;

    public ExpiredReservationCandidateReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Long> findExpiredListingIds(LocalDateTime before, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT id
                        FROM listing
                        WHERE status = 'RESERVED'
                          AND reserved_until < :before
                          AND deleted_at IS NULL
                        ORDER BY reserved_until ASC
                        LIMIT :limit
                        """)
                .param("before", before)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }
}
