package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.ListingStatusHistory;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매물 상태 전이 유스케이스. Listing 엔티티의 전이 메서드를 호출하고, 변경 전/후 상태를
 * ListingStatusHistory로 함께 저장한다. 비즈니스 규칙(사전조건 검증)은 Listing 엔티티가 담당하고
 * 이 서비스는 조회, 전이 호출, 이력 저장의 순서만 조율한다.
 */
@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository listingRepository;
    private final ListingStatusHistoryRepository listingStatusHistoryRepository;
    private final Clock clock;
    private final long reservationTtlMinutes;

    public ListingService(
            ListingRepository listingRepository,
            ListingStatusHistoryRepository listingStatusHistoryRepository,
            Clock clock,
            @Value("${limit.product.reservation-ttl-minutes:10}") long reservationTtlMinutes) {
        this.listingRepository = listingRepository;
        this.listingStatusHistoryRepository = listingStatusHistoryRepository;
        this.clock = clock;
        this.reservationTtlMinutes = reservationTtlMinutes;
    }

    @Transactional(readOnly = true)
    public ListingReservationView get(Long listingId) {
        Listing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return toReservationView(listing);
    }

    @Transactional(readOnly = true)
    public boolean isReservationActive(Long listingId, Long buyerId) {
        Listing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));
        return listing.getStatus() == ListingStatus.RESERVED
                && buyerId.equals(listing.getBuyerId())
                && listing.getReservedUntil() != null
                && listing.getReservedUntil().isAfter(LocalDateTime.now(clock));
    }

    @Transactional
    public ListingReservationView reserve(Long listingId, Long buyerId) {
        LocalDateTime reservedUntil = LocalDateTime.now(clock).plusMinutes(reservationTtlMinutes);
        return toReservationView(
                transition(
                        listingId,
                        buyerId,
                        null,
                        listing -> listing.reserve(buyerId, reservedUntil)));
    }

    @Transactional
    public Listing cancelReservation(Long listingId, Long actorId, String reason) {
        return transition(listingId, actorId, reason, Listing::cancelReservation);
    }

    @Transactional
    public Listing expireReservation(Long listingId, String reason) {
        return transition(listingId, null, reason, Listing::expireReservation);
    }

    @Transactional
    public Listing markPaid(Long listingId, Long buyerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return transition(listingId, buyerId, null, listing -> listing.markPaid(buyerId, now));
    }

    /**
     * PG 대사(reconcile)로 Toss 승인을 확인한 결제를 복구할 때만 사용한다. 예약 유예 시간이 이미
     * 지났어도 결제완료로 전환하는 예외 경로라, 일반 결제 확정({@link #markPaid})과는 별도로
     * 이력에 남긴다.
     */
    @Transactional
    public Listing markPaidRecoveredFromPg(Long listingId, Long buyerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return transition(
                listingId,
                buyerId,
                "PG 승인 대사로 만료된 예약을 결제완료로 복구함",
                listing -> listing.markPaidRecoveredFromPg(buyerId, now));
    }

    @Transactional
    public Listing markInspecting(Long listingId) {
        return transition(listingId, null, null, Listing::markInspecting);
    }

    @Transactional
    public Listing confirm(Long listingId, Long actorId) {
        return transition(listingId, actorId, null, Listing::confirm);
    }

    @Transactional
    public Listing settle(Long listingId) {
        return transition(listingId, null, null, Listing::settle);
    }

    private Listing transition(
            Long listingId, Long actorId, String reason, Consumer<Listing> transitionFn) {
        Listing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LISTING_NOT_FOUND));

        ListingStatus fromStatus = listing.getStatus();
        transitionFn.accept(listing);

        listingStatusHistoryRepository.save(
                ListingStatusHistory.record(listing, fromStatus, listing.getStatus(), reason, actorId));

        log.info(
                "listing status transitioned: listingId={}, from={}, to={}, actorId={}",
                listingId,
                fromStatus,
                listing.getStatus(),
                actorId);

        return listing;
    }

    private ListingReservationView toReservationView(Listing listing) {
        return new ListingReservationView(listing.getSellerId(), listing.getPrice());
    }
}
