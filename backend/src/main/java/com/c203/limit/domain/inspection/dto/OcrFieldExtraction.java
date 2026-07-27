package com.c203.limit.domain.inspection.dto;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.math.BigDecimal;

/** OcrClient가 이미지 한 장에서 감지한 필드 하나의 원시 인식 결과. */
public record OcrFieldExtraction(
        OcrFieldType fieldType, String rawText, String parsedValue, BigDecimal confidence) {}
