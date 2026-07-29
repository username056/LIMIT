package com.c203.limit.domain.inspection.reinspection.event;

import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import java.util.List;

/**
 * 재검수 요청·완료 시 채팅 도메인에 알림을 전달하기 위한 이벤트. 인프로세스 ApplicationEventPublisher로
 * 발행되며, 요청/완료 두 시점 모두 이 이벤트 하나를 재사용한다. actorId/recipientId로 방향(누가 행동했고
 * 누구에게 알려야 하는지)을 구분한다.
 */
public record ReinspectionNotificationEvent(
        Long reinspectionRequestId,
        String requestKey,
        Long chatRoomId,
        Long actorId,
        Long recipientId,
        long pendingRequestCount,
        String reason,
        List<Item> items) {

    public static ReinspectionNotificationEvent of(
            ReinspectionRequest request,
            List<ReinspectionRequestItem> items,
            Long actorId,
            Long recipientId,
            long pendingRequestCount) {
        return new ReinspectionNotificationEvent(
                request.getId(),
                request.getRequestKey(),
                request.getChatRoomId(),
                actorId,
                recipientId,
                pendingRequestCount,
                request.getReason(),
                items.stream().map(Item::from).toList());
    }

    public record Item(Long checklistItemId, String itemName, String requestContent, int displayOrder) {
        static Item from(ReinspectionRequestItem item) {
            return new Item(
                    item.getListingChecklistItem().getId(),
                    item.getItemNameSnapshot(),
                    item.getRequestContent(),
                    item.getDisplayOrder());
        }
    }
}
