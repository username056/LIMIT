package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.util.Set;

/** 증거 스크린샷("설정 &gt; 시스템 &gt; 정보" 화면) OCR에서 기대하는 필드 타입 목록. */
public final class OcrFieldExpectations {

    public static final Set<OcrFieldType> SCREENSHOT_FIELD_TYPES =
            Set.of(
                    OcrFieldType.MODEL_NAME,
                    OcrFieldType.CPU,
                    OcrFieldType.RAM,
                    OcrFieldType.GPU,
                    OcrFieldType.STORAGE_CAPACITY,
                    OcrFieldType.OS_VERSION);

    private OcrFieldExpectations() {}
}
