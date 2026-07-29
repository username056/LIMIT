package com.c203.limit.domain.inspection.reinspection.dto.response;

import com.c203.limit.domain.inspection.entity.ReinspectionRequest;
import com.c203.limit.domain.inspection.entity.ReinspectionRequestItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ReinspectionRequestResponse", description = "재검수 요청")
public class ReinspectionRequestResponse {

    @Schema(example = "1")
    private final Long id;

    @Schema(example = "b3b1c7a2-3c1a-4b1a-9c1a-1a2b3c4d5e6f")
    private final String requestKey;

    @Schema(example = "10")
    private final Long listingId;

    @Schema(example = "25")
    private final Long chatRoomId;

    @Schema(example = "100")
    private final Long buyerId;

    @Schema(example = "200")
    private final Long sellerId;

    @Schema(example = "제품 상태를 조금 더 자세히 확인하고 싶습니다.")
    private final String reason;

    @Schema(example = "REQUESTED")
    private final String status;

    @Schema(example = "2026-07-22T14:30:00")
    private final LocalDateTime requestedAt;

    @Schema(example = "2026-07-22T16:00:00")
    private final LocalDateTime completedAt;

    private final List<ReinspectionRequestItemResponse> items;

    public static ReinspectionRequestResponse of(ReinspectionRequest request, List<ReinspectionRequestItem> items) {
        return new ReinspectionRequestResponse(
                request.getId(),
                request.getRequestKey(),
                request.getListingId(),
                request.getChatRoomId(),
                request.getBuyerId(),
                request.getSellerId(),
                request.getReason(),
                request.getStatus().name(),
                request.getRequestedAt(),
                request.getCompletedAt(),
                items.stream().map(ReinspectionRequestItemResponse::from).toList());
    }
}
