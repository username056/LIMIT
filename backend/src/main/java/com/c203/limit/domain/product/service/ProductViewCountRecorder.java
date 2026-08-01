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
 * <p>상세 조회 트랜잭션과 분리한다({@link Propagation#REQUIRES_NEW}). 트랜잭션 종료 단계까지
 * 포함한 실패 처리는 이 컴포넌트를 호출하는 비동기 디스패처가 맡는다.
 *
 * <p>중복 조회 방지는 아직 없다. 같은 사용자가 새로고침하면 그만큼 올라간다. 30분 단위 중복
 * 제외는 조회자 식별과 만료가 필요해 Redis나 별도 테이블이 있어야 한다.
 */
@Service
public class ProductViewCountRecorder {

    private static final Logger log = LoggerFactory.getLogger(ProductViewCountRecorder.class);

    private final ListingRepository listingRepository;

    public ProductViewCountRecorder(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    /**
     * @return 집계됐으면 true. 공개 매물이 아니면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(Long productId) {
        if (productId == null) {
            log.warn("product view count ignored: productId is missing");
            return false;
        }
        return listingRepository.increaseViewCount(productId) > 0;
    }
}
