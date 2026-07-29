package com.c203.limit.domain.payment.repository;

import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBuyerIdAndIdempotencyKey(Long buyerId, String idempotencyKey);

    Optional<Payment> findByListingIdAndStatus(Long listingId, PaymentStatus status);
}
