package com.c203.limit.domain.product.service;

import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.entity.ListingStatusHistory;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.repository.ListingStatusHistoryRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매물 상태 전이 유스케이스. Listing 엔티티의 전이 메서드를 호출하고, 변경 전/후 상태를
 * ListingStatusHistory로 함께 저장한다. 비즈니스 규칙(사전조건 검증)은 Listing 엔티티가 담당하고
 * 이 서비스는 조회, 전이 호출, 이력 저장의 순서만 조율한다.
 */
@Service
public class ListingService {

    private final ListingRepository listingRepository;
    private final ListingStatusHistoryRepository listingStatusHistoryRepository;

    public ListingService(
            ListingRepository listingRepository,
            ListingStatusHistoryRepository listingStatusHistoryRepository) {
        this.listingRepository = listingRepository;
        this.listingStatusHistoryRepository = listingStatusHistoryRepository;
    }

    @Transactional
    public Listing reserve(Long listingId, Long buyerId) {
        return transition(listingId, buyerId, null, listing -> listing.reserve(buyerId));
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
    public Listing markPaid(Long listingId) {
        return transition(listingId, null, null, Listing::markPaid);
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

        return listing;
    }
}
