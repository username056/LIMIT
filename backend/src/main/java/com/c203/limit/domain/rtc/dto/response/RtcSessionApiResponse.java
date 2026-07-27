package com.c203.limit.domain.rtc.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RtcSessionApiResponse", description = "WebRTC 세션 응답")
public record RtcSessionApiResponse(RtcSessionResponse data, Object meta) {}
