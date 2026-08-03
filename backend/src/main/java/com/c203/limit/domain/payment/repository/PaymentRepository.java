package com.c203.limit.domain.payment.repository;

import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBuyerIdAndIdempotencyKey(Long buyerId, String idempotencyKey);

    Optional<Payment> findByListingIdAndStatus(Long listingId, PaymentStatus status);

    /**
     * 주문 내역 목록. 상태를 명시적으로 허용 목록으로 걸러야 한다 — REQUESTED(진입 전)뿐 아니라
     * CANCELLED·EXPIRED(승인 전 취소·이탈)·FAILED(승인 거절)도 "주문"이 아니므로 제외 대상이다.
     * 무엇을 뺄지가 아니라 무엇을 보여줄지를 기준으로 걸러야, 새 상태가 추가돼도 실수로 새는 일이
     * 없다.
     */
    List<Payment> findByBuyer_IdAndStatusInOrderByRequestedAtDesc(
            Long buyerId, Collection<PaymentStatus> statuses);

    /**
     * 같은 매물에 같은 구매자의 활성 결제 요청이 이미 있는지 조회한다. 정상 흐름에서는 매물 하나에
     * 구매자 하나당 REQUESTED가 최대 1건이어야 하지만, 과거 데이터 이상으로 여러 건이 남아 있을
     * 가능성까지 고려해 최신 1건만 가져온다(Top으로 제한해 다건이어도 예외 없이 동작한다).
     */
    Optional<Payment> findTopByListingIdAndBuyer_IdAndStatusOrderByRequestedAtDesc(
            Long listingId, Long buyerId, PaymentStatus status);

    /** 위 조회가 "최대 1건" 가정을 깨는 데이터를 조용히 덮지 않도록, 실제 개수를 확인하는 용도다. */
    long countByListingIdAndBuyer_IdAndStatus(Long listingId, Long buyerId, PaymentStatus status);
}
