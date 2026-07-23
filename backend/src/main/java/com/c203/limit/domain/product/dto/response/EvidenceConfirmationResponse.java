package com.c203.limit.domain.product.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "EvidenceConfirmationResponse", description = "구매자의 증거 확인 결과")
public class EvidenceConfirmationResponse {

    @Schema(example = "9003")
    private final Long evidenceId;

    @Schema(example = "CONFIRMED")
    private final String status;

    @Schema(example = "77")
    private final Long confirmedBy;

    @Schema(example = "2026-07-22T13:00:00+09:00")
    private final OffsetDateTime confirmedAt;
}
