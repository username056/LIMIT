package com.c203.limit.domain.inspection.reinspection.service;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import com.c203.limit.domain.inspection.enums.ReinspectionStatus;
import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestItemCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.reinspection.event.ReinspectionNotificationEvent;
import com.c203.limit.domain.inspection.reinspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.reinspection.repository.ReinspectionRequestRepository;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReinspectionRequestService {

    private static final Logger log = LoggerFactory.getLogger(ReinspectionRequestService.class);

    private final ReinspectionRequestRepository reinspectionRequestRepository;
    private final ReinspectionRequestItemRepository reinspectionRequestItemRepository;
    private final ListingChecklistItemRepository listingChecklistItemRepository;
    private final ListingOwnerReader listingOwnerReader;
    private final ChatRoomService chatRoomService;
    private final ApplicationEventPublisher eventPublisher;

    public ReinspectionRequestService(
            ReinspectionRequestRepository reinspectionRequestRepository,
            ReinspectionRequestItemRepository reinspectionRequestItemRepository,
            ListingChecklistItemRepository listingChecklistItemRepository,
            ListingOwnerReader listingOwnerReader,
            ChatRoomService chatRoomService,
            ApplicationEventPublisher eventPublisher) {
        this.reinspectionRequestRepository = reinspectionRequestRepository;
        this.reinspectionRequestItemRepository = reinspectionRequestItemRepository;
        this.listingChecklistItemRepository = listingChecklistItemRepository;
        this.listingOwnerReader = listingOwnerReader;
        this.chatRoomService = chatRoomService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ReinspectionRequestResponse request(Long listingId, Long buyerId, ReinspectionRequestCreateRequest request) {
        ListingOwnerReader.ListingOwnerInfo listing =
                listingOwnerReader
                        .findById(listingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Long sellerId = listing.sellerId();
        if (buyerId.equals(sellerId)) {
            throw new BusinessException(ErrorCode.REINSPECTION_SELF_REQUEST_NOT_ALLOWED);
        }

        Long chatRoomId = chatRoomService.getOrCreateChatRoom(listingId, buyerId, sellerId);

        List<ListingChecklistItem> checklistItems = new ArrayList<>();
        for (ReinspectionRequestItemCreateRequest itemRequest : request.getItems()) {
            ListingChecklistItem checklistItem =
                    listingChecklistItemRepository
                            .findByIdAndListingId(itemRequest.getChecklistItemId(), listingId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.REINSPECTION_ITEM_LISTING_MISMATCH));
            checklistItems.add(checklistItem);
        }

        String requestKey = UUID.randomUUID().toString();
        ReinspectionRequest reinspectionRequest =
                reinspectionRequestRepository.save(
                        ReinspectionRequest.request(
                                listingId, chatRoomId, requestKey, request.getReason(), buyerId, sellerId));

        List<ReinspectionRequestItem> items = new ArrayList<>();
        for (int i = 0; i < checklistItems.size(); i++) {
            ReinspectionRequestItemCreateRequest itemRequest = request.getItems().get(i);
            items.add(
                    ReinspectionRequestItem.create(
                            reinspectionRequest, checklistItems.get(i), itemRequest.getRequestContent(), i + 1));
        }
        reinspectionRequestItemRepository.saveAll(items);

        log.info(
                "reinspection requested: requestKey={}, listingId={}, chatRoomId={}, buyerId={}",
                requestKey,
                listingId,
                chatRoomId,
                buyerId);

        long pendingRequestCount =
                reinspectionRequestRepository.countByChatRoomIdAndStatus(chatRoomId, ReinspectionStatus.REQUESTED);
        eventPublisher.publishEvent(
                ReinspectionNotificationEvent.of(reinspectionRequest, items, buyerId, sellerId, pendingRequestCount));

        return ReinspectionRequestResponse.of(reinspectionRequest, items);
    }

    @Transactional
    public ReinspectionRequestResponse complete(String requestKey, Long sellerId) {
        ReinspectionRequest reinspectionRequest =
                reinspectionRequestRepository
                        .findByRequestKey(requestKey)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REINSPECTION_REQUEST_NOT_FOUND));

        if (!reinspectionRequest.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.REINSPECTION_ACCESS_DENIED);
        }
        if (reinspectionRequest.getStatus() != ReinspectionStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.REINSPECTION_ALREADY_PROCESSED);
        }

        reinspectionRequest.complete();

        List<ReinspectionRequestItem> items =
                reinspectionRequestItemRepository.findByReinspectionRequestIdOrderByDisplayOrderAsc(
                        reinspectionRequest.getId());

        log.info(
                "reinspection completed: requestKey={}, listingId={}, sellerId={}",
                requestKey,
                reinspectionRequest.getListingId(),
                sellerId);

        long pendingRequestCount =
                reinspectionRequestRepository.countByChatRoomIdAndStatus(
                        reinspectionRequest.getChatRoomId(), ReinspectionStatus.REQUESTED);
        eventPublisher.publishEvent(
                ReinspectionNotificationEvent.of(
                        reinspectionRequest,
                        items,
                        sellerId,
                        reinspectionRequest.getBuyerId(),
                        pendingRequestCount));

        return ReinspectionRequestResponse.of(reinspectionRequest, items);
    }
}
