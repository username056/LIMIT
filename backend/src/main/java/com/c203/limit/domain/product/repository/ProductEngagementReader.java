package com.c203.limit.domain.product.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 상품 상세에 함께 보여 줄 관심도 수치를 읽는다.
 *
 * <p>좋아요 수는 wishlist, 문의 수는 chat_room의 행 수다. 둘 다 상품 도메인이 직접 소유하지
 * 않는 테이블이라 엔티티 연관을 새로 걸지 않고 집계만 읽는다. 연관을 걸면 상품을 지울 때
 * 위시리스트·채팅방까지 얽혀 삭제 규칙이 도메인 밖으로 번진다.
 *
 * <p>조회수는 listing.view_count에 이미 있어 여기서 읽지 않는다.
 *
 * <p>상세는 상품 하나만 읽는 화면이라 집계 두 개를 한 번에 가져온다. 목록에서 쓰려면
 * listing_id IN (:ids) 형태로 바꿔 N+1을 피해야 한다.
 */
@Repository
public class ProductEngagementReader {
    private final JdbcClient jdbcClient;

    public ProductEngagementReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ProductEngagement findByListingId(Long listingId) {
        return jdbcClient.sql("""
                        SELECT (SELECT COUNT(*) FROM wishlist
                                 WHERE wishlist.listing_id = :listingId) AS favorite_count,
                               (SELECT COUNT(*) FROM chat_room
                                 WHERE chat_room.listing_id = :listingId) AS chat_room_count
                        """)
                .param("listingId", listingId)
                .query((resultSet, rowNum) -> new ProductEngagement(
                        resultSet.getLong("favorite_count"),
                        resultSet.getLong("chat_room_count")))
                .single();
    }

    public record ProductEngagement(long favoriteCount, long chatRoomCount) {}
}
