package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EndRtcSessionRequest(
        @NotBlank String endReason,
        @Size(max = 1000) String memo,
        List<@Valid RtcChecklistResultRequest> checklistResults) {}
