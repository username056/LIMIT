package com.c203.limit.domain.inspection.client;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 실제 OCR 없이 자동 구조화 파이프라인(정규화·상태 판정·저장)을 검증하기 위한 견본 구현체. 운영 빈은
 * {@link NaverClovaSystemInfoOcrClient}가 {@code @Primary}로 우선한다.
 */
@Component
public class MockOcrClient implements OcrClient {

    private static final String MODEL_VERSION = "mock-v1";

    private static final Map<OcrFieldType, String> SAMPLE_RAW_TEXT = new EnumMap<>(OcrFieldType.class);

    static {
        SAMPLE_RAW_TEXT.put(OcrFieldType.MODEL_NAME, "Galaxy Book4 Pro");
        SAMPLE_RAW_TEXT.put(OcrFieldType.STORAGE_CAPACITY, "512 GB");
        SAMPLE_RAW_TEXT.put(OcrFieldType.OS_VERSION, "Windows 11 Pro");
        SAMPLE_RAW_TEXT.put(OcrFieldType.CPU, "Intel Core Ultra 7");
        SAMPLE_RAW_TEXT.put(OcrFieldType.RAM, "16 GB");
        SAMPLE_RAW_TEXT.put(OcrFieldType.GPU, "Intel Arc Graphics");
    }

    @Override
    public List<OcrFieldExtraction> extractFields(
            String imageUrl, String mimeType, Set<OcrFieldType> expectedFieldTypes) {
        return expectedFieldTypes.stream()
                .filter(SAMPLE_RAW_TEXT::containsKey)
                .map(
                        fieldType -> {
                            String rawText = SAMPLE_RAW_TEXT.get(fieldType);
                            return new OcrFieldExtraction(
                                    fieldType, rawText, rawText.trim(), new BigDecimal("0.900"));
                        })
                .toList();
    }

    @Override
    public String getModelVersion() {
        return MODEL_VERSION;
    }
}
