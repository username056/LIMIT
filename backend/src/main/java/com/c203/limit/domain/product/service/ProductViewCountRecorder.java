package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.repository.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 상품 상세 조회수 집계.
 *
 * <p>상세 조회 트랜잭션과 분리한다({@link Propagation#REQUIRES_NEW}). 조회수 증가가 실패해도
 * 상세 응답은 나가야 한다 — 집계는 부가 기능이고, 이걸로 조회 자체가 막히면 손해가 훨씬 크다.
 * 반대로 상세 조회가 읽기 전용 트랜잭션이라 같은 트랜잭션에서는 쓰기를 할 수도 없다.
 *
 * <p>중복 조회 방지는 아직 없다. 같은 사용자가 새로고침하면 그만큼 올라간다. 30분 단위 중복
 * 제외는 조회자 식별과 만료가 필요해 Redis나 별도 테이블이 있어야 하고, 그 전까지 인기순은
 * "많이 열린 순"으로만 읽어야 한다.
 */
@Service
public class ProductViewCountRecorder {

    private static final Logger log = LoggerFactory.getLogger(ProductViewCountRecorder.class);

    private final ListingRepository listingRepository;

    public ProductViewCountRecorder(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    /**
     * @return 집계됐으면 true. 공개 매물이 아니거나 실패하면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(Long productId) {
        if (productId == null) return false;
        try {
            return listingRepository.increaseViewCount(productId) > 0;
        } catch (RuntimeException exception) {
            log.warn(
                    "product view count not recorded: productId={}, cause={}",
                    productId,
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
