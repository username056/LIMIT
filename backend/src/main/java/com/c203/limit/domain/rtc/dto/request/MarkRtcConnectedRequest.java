package com.c203.limit.domain.rtc.dto.request;

import jakarta.validation.constraints.Pattern;

public record MarkRtcConnectedRequest(@Pattern(regexp = "P2P|TURN") String connectionType) {}
