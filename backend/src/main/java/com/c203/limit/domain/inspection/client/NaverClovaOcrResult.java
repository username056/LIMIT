package com.c203.limit.domain.inspection.client;

import java.math.BigDecimal;

public record NaverClovaOcrResult(String rawText, BigDecimal confidence, String modelVersion) {}
