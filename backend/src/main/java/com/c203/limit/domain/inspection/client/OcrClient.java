package com.c203.limit.domain.inspection.client;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.util.List;
import java.util.Set;

/**
 * 증거 이미지 한 장에서 기대 필드 목록을 한 번에 인식하는 OCR 호출부 추상화. 지금은 {@link MockOcrClient}로 대체되어 있으며,
 * 추후 네이버 클로바 OCR 연동 구현체로 교체될 예정이다.
 */
public interface OcrClient {

    /** imageUrl에서 expectedFieldTypes에 해당하는 필드를 감지한 만큼만 반환한다(전부 감지 못할 수 있음). */
    List<OcrFieldExtraction> extractFields(
            String imageUrl, String mimeType, Set<OcrFieldType> expectedFieldTypes);

    /** 이번 인식에 사용된 OCR 모델 버전. ocr_result.ocr_model_version에 그대로 저장된다. */
    String getModelVersion();
}
