package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.chat.service.ChatRoomService;
import com.c203.limit.domain.inspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import com.c203.limit.domain.inspection.enums.ReinspectionStatus;
import com.c203.limit.domain.inspection.event.ReinspectionNotificationEvent;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestItemRepository;
import com.c203.limit.domain.inspection.repository.ReinspectionRequestRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReinspectionRequestService {
    private static final Logger log = LoggerFactory.getLogger(ReinspectionRequestService.class);

    private final ListingOwnerReader listingOwnerReader;
    private final ChatRoomService chatRoomService;
    private final ListingChecklistItemRepository checklistItemRepository;
    private final ReinspectionRequestRepository requestRepository;
    private final ReinspectionRequestItemRepository requestItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReinspectionRequestService(
            ListingOwnerReader listingOwnerReader,
            ChatRoomService chatRoomService,
            ListingChecklistItemRepository checklistItemRepository,
            ReinspectionRequestRepository requestRepository,
            ReinspectionRequestItemRepository requestItemRepository,
            ApplicationEventPublisher eventPublisher) {
        this.listingOwnerReader = listingOwnerReader;
        this.chatRoomService = chatRoomService;
        this.checklistItemRepository = checklistItemRepository;
        this.requestRepository = requestRepository;
        this.requestItemRepository = requestItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ReinspectionRequestResponse create(
            Long listingId, Long buyerId, ReinspectionRequestCreateRequest command) {
        Long sellerId = listingOwnerReader.findById(listingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                .sellerId();
        if (sellerId.equals(buyerId)) {
            throw new BusinessException(ErrorCode.REINSPECTION_BUYER_REQUIRED);
        }
        Long chatRoomId = chatRoomService.getOrCreateChatRoom(listingId, buyerId, sellerId);

        List<Long> itemIds = command.items().stream()
                .map(ReinspectionRequestCreateRequest.Item::checklistItemId)
                .toList();
        if (itemIds.stream().distinct().count() != itemIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Map<Long, ListingChecklistItem> checklistItems = new HashMap<>();
        checklistItemRepository.findAllByIdInAndListingId(itemIds, listingId)
                .forEach(item -> checklistItems.put(item.getId(), item));
        if (checklistItems.size() != itemIds.size()) {
            throw new BusinessException(ErrorCode.REINSPECTION_ITEM_NOT_FOUND);
        }

        ReinspectionRequest request = requestRepository.save(ReinspectionRequest.request(
                listingId,
                chatRoomId,
                UUID.randomUUID().toString(),
                command.reason().trim(),
                buyerId,
                sellerId));
        List<ReinspectionRequestItem> savedItems = requestItemRepository.saveAll(
                java.util.stream.IntStream.range(0, command.items().size())
                        .mapToObj(index -> {
                            var item = command.items().get(index);
                            return ReinspectionRequestItem.create(
                                    request,
                                    checklistItems.get(item.checklistItemId()),
                                    item.requestContent().trim(),
                                    index);
                        })
                        .toList());
        int pendingRequestCount = Math.toIntExact(
                requestRepository.countByChatRoomIdAndStatus(
                        chatRoomId, ReinspectionStatus.REQUESTED));
        eventPublisher.publishEvent(new ReinspectionNotificationEvent(
                UUID.randomUUID(),
                ReinspectionNotificationEvent.Type.REQUESTED,
                request.getId(),
                request.getRequestKey(),
                chatRoomId,
                buyerId,
                sellerId,
                pendingRequestCount,
                request.getReason(),
                toEventItems(savedItems)));
        log.info(
                "reinspection requested: requestKey={}, listingId={}, buyerId={}, sellerId={}",
                request.getRequestKey(), listingId, buyerId, sellerId);
        return ReinspectionRequestResponse.from(request, savedItems);
    }

    @Transactional
    public ReinspectionRequestResponse complete(String requestKey, Long sellerId) {
        ReinspectionRequest request = requestRepository.findByRequestKey(requestKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.REINSPECTION_REQUEST_NOT_FOUND));
        if (!request.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.REINSPECTION_SELLER_REQUIRED);
        }
        if (request.getStatus() != ReinspectionStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.REINSPECTION_INVALID_STATE);
        }
        List<ReinspectionRequestItem> items =
                requestItemRepository.findByReinspectionRequestIdOrderByDisplayOrderAsc(request.getId());
        request.complete();
        int pendingRequestCount = Math.toIntExact(
                requestRepository.countByChatRoomIdAndStatus(
                        request.getChatRoomId(), ReinspectionStatus.REQUESTED));
        eventPublisher.publishEvent(new ReinspectionNotificationEvent(
                UUID.randomUUID(),
                ReinspectionNotificationEvent.Type.COMPLETED,
                request.getId(),
                request.getRequestKey(),
                request.getChatRoomId(),
                sellerId,
                request.getBuyerId(),
                pendingRequestCount,
                request.getReason(),
                toEventItems(items)));
        log.info(
                "reinspection completed: requestKey={}, listingId={}, sellerId={}",
                request.getRequestKey(), request.getListingId(), sellerId);
        return ReinspectionRequestResponse.from(request, items);
    }

    private List<ReinspectionNotificationEvent.Item> toEventItems(
            List<ReinspectionRequestItem> items) {
        return items.stream()
                .map(item -> new ReinspectionNotificationEvent.Item(
                        item.getItemNameSnapshot(), item.getRequestContent()))
                .toList();
    }
}
