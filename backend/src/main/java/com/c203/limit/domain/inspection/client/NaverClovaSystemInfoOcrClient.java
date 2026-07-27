package com.c203.limit.domain.inspection.client;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.parser.SystemInfoScreenshotParser;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Windows "설정 &gt; 시스템 &gt; 정보" 화면 스크린샷을 네이버 클로바 OCR로 인식하고
 * {@link SystemInfoScreenshotParser}로 필드별로 구조화하는 실제 운영 구현체.
 *
 * <p>클로바가 응답하는 {@code version} 필드는 이미지별 값이 아니라 OCR 엔진/도메인에 고정된 값이라 빈을
 * 상태 없이 유지하기 위해 상수로 취급한다.
 */
@Component
@Primary
public class NaverClovaSystemInfoOcrClient implements OcrClient {

    private static final String MODEL_VERSION = "naver-clova-general-v2";

    private final NaverClovaOcrClient naverClovaOcrClient;
    private final SystemInfoScreenshotParser parser;

    public NaverClovaSystemInfoOcrClient(
            NaverClovaOcrClient naverClovaOcrClient, SystemInfoScreenshotParser parser) {
        this.naverClovaOcrClient = naverClovaOcrClient;
        this.parser = parser;
    }

    @Override
    public List<OcrFieldExtraction> extractFields(
            String imageUrl, String mimeType, Set<OcrFieldType> expectedFieldTypes) {
        String format = resolveImageFormat(mimeType);
        List<OcrToken> tokens = naverClovaOcrClient.recognizeFields(imageUrl, format);
        return parser.parse(tokens, expectedFieldTypes);
    }

    @Override
    public String getModelVersion() {
        return MODEL_VERSION;
    }

    private String resolveImageFormat(String mimeType) {
        if (mimeType == null) {
            throw new BusinessException(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT);
        }
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/bmp" -> "bmp";
            case "image/tiff" -> "tiff";
            default -> throw new BusinessException(ErrorCode.OCR_UNSUPPORTED_IMAGE_FORMAT);
        };
    }
}
