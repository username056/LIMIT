package com.c203.limit.domain.payment.service;

import com.c203.limit.domain.payment.dto.response.OrderSummaryResponse;
import com.c203.limit.domain.payment.entity.Payment;
import com.c203.limit.domain.payment.entity.PaymentStatus;
import com.c203.limit.domain.payment.repository.ListingOrderSummaryReader;
import com.c203.limit.domain.payment.repository.ListingOrderSummaryReader.ListingOrderSummary;
import com.c203.limit.domain.payment.repository.PaymentRepository;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 기준 주문 내역 목록 유스케이스. Payment가 목록의 기준이고, 매물 표시 정보(상품명·상태·
 * 대표 이미지)는 {@link ListingOrderSummaryReader}로 조회해 product 도메인 Entity를 직접
 * 참조하지 않는다.
 */
@Service
public class OrderQueryService {
    private static final Logger log = LoggerFactory.getLogger(OrderQueryService.class);
    private static final ZoneId ORDER_TIME_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 주문 내역에 노출할 결제 상태. 승인 전 취소·이탈(REQUESTED/CANCELLED/EXPIRED)과 승인 거절
     * (FAILED)은 "주문"이 아니라서 뺀다 — 실제 돈이 오간(APPROVED) 이후의 흐름만 주문/환불 내역
     * 대상이다.
     */
    private static final Set<PaymentStatus> ORDER_HISTORY_STATUSES = EnumSet.of(
            PaymentStatus.APPROVED,
            PaymentStatus.REFUND_REQUESTED,
            PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final ListingOrderSummaryReader listingOrderSummaryReader;
    private final MediaUrlResolver mediaUrlResolver;

    public OrderQueryService(
            PaymentRepository paymentRepository,
            ListingOrderSummaryReader listingOrderSummaryReader,
            MediaUrlResolver mediaUrlResolver) {
        this.paymentRepository = paymentRepository;
        this.listingOrderSummaryReader = listingOrderSummaryReader;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> listOrders(Long buyerId) {
        List<Payment> payments =
                paymentRepository.findByBuyer_IdAndStatusInOrderByRequestedAtDesc(
                        buyerId, ORDER_HISTORY_STATUSES);
        if (payments.isEmpty()) {
            return List.of();
        }

        List<Long> listingIds = payments.stream().map(Payment::getListingId).distinct().toList();
        Map<Long, ListingOrderSummary> listingsById =
                listingOrderSummaryReader.findByIds(listingIds).stream()
                        .collect(Collectors.toMap(ListingOrderSummary::listingId, Function.identity()));

        return payments.stream()
                .map(payment -> toOrderSummary(payment, listingsById.get(payment.getListingId())))
                .toList();
    }

    private OrderSummaryResponse toOrderSummary(Payment payment, ListingOrderSummary listing) {
        if (listing == null) {
            // 결제가 존재하면 매물도 항상 있어야 한다 — 못 찾았다는 건 데이터 정합성 문제라
            // 운영자가 조사할 신호를 남긴다. 목록 자체는 계속 내려주되(상품명 등은 null) 막지 않는다.
            log.warn(
                    "order summary missing listing data: paymentId={}, listingId={}",
                    payment.getId(),
                    payment.getListingId());
        }
        String thumbnailUrl =
                listing == null ? null : mediaUrlResolver.resolve(listing.s3Key(), listing.cdnUrl());
        return new OrderSummaryResponse(
                payment.getId(),
                payment.getListingId(),
                listing == null ? null : listing.title(),
                thumbnailUrl,
                payment.getRequestedAmount(),
                payment.getStatus().name(),
                listing == null ? null : listing.status(),
                offset(payment.getRequestedAt()),
                offset(payment.getApprovedAt()));
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(ORDER_TIME_ZONE).toOffsetDateTime();
    }
}
