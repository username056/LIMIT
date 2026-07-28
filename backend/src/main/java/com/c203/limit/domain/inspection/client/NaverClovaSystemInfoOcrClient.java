package com.c203.limit.domain.inspection.client;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.parser.SystemInfoScreenshotParser;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Windows "설정 &gt; 시스템 &gt; 정보" 화면 스크린샷을 네이버 클로바 OCR로 인식하고
 * {@link SystemInfoScreenshotParser}로 필드별로 구조화하는 실제 운영 구현체.
 *
 * <p>기대 필드 중 {@value #RETRY_MISSING_THRESHOLD}개 이상을 못 찾으면(스크린샷 6개 필드 기준 3개 이상),
 * 원본 이미지를 그레이스케일·명암 대비 강화 전처리한 뒤 한 번만 다시 인식을 시도한다. 재시도 결과는 1차
 * 인식에서 이미 찾은 필드는 그대로 두고, 못 찾았던 필드만 채워 넣는 데 쓴다(전처리가 오히려 잘 인식되던 필드를
 * 망칠 수 있어 무조건 덮어쓰지 않는다).
 *
 * <p>클로바가 응답하는 {@code version} 필드는 이미지별 값이 아니라 OCR 엔진/도메인에 고정된 값이라 빈을
 * 상태 없이 유지하기 위해 상수로 취급한다.
 */
@Component
@Primary
public class NaverClovaSystemInfoOcrClient implements OcrClient {

    private static final String MODEL_VERSION = "naver-clova-general-v2";
    private static final String PREPROCESSED_IMAGE_FORMAT = "png";

    /** 스크린샷 기대 필드 6개 중 3개 이상 못 찾으면 전처리 후 재시도한다. */
    private static final int RETRY_MISSING_THRESHOLD = 3;

    private final NaverClovaOcrClient naverClovaOcrClient;
    private final SystemInfoScreenshotParser parser;
    private final OcrImagePreprocessor imagePreprocessor;

    public NaverClovaSystemInfoOcrClient(
            NaverClovaOcrClient naverClovaOcrClient,
            SystemInfoScreenshotParser parser,
            OcrImagePreprocessor imagePreprocessor) {
        this.naverClovaOcrClient = naverClovaOcrClient;
        this.parser = parser;
        this.imagePreprocessor = imagePreprocessor;
    }

    @Override
    public List<OcrFieldExtraction> extractFields(
            String imageUrl, String mimeType, Set<OcrFieldType> expectedFieldTypes) {
        String format = resolveImageFormat(mimeType);
        byte[] imageBytes = naverClovaOcrClient.fetchImage(imageUrl);

        List<OcrFieldExtraction> extractions = recognizeAndParse(imageBytes, format, expectedFieldTypes);

        if (shouldRetryWithPreprocessing(extractions, expectedFieldTypes)) {
            List<OcrFieldExtraction> retryExtractions = retryWithPreprocessing(imageBytes, expectedFieldTypes);
            extractions = mergeFillingMissingFields(extractions, retryExtractions);
        }

        return extractions;
    }

    @Override
    public String getModelVersion() {
        return MODEL_VERSION;
    }

    private List<OcrFieldExtraction> recognizeAndParse(
            byte[] imageBytes, String format, Set<OcrFieldType> expectedFieldTypes) {
        List<OcrToken> tokens = naverClovaOcrClient.recognizeFields(imageBytes, format);
        return parser.parse(tokens, expectedFieldTypes);
    }

    private boolean shouldRetryWithPreprocessing(
            List<OcrFieldExtraction> extractions, Set<OcrFieldType> expectedFieldTypes) {
        long missingCount =
                expectedFieldTypes.stream()
                        .filter(fieldType -> extractions.stream().noneMatch(e -> e.fieldType() == fieldType))
                        .count();
        return missingCount >= RETRY_MISSING_THRESHOLD;
    }

    /** 전처리·재시도 자체가 실패해도(이미지 디코딩 불가, 재인식 실패 등) 1차 결과는 그대로 살리기 위해 예외를 삼킨다. */
    private List<OcrFieldExtraction> retryWithPreprocessing(
            byte[] originalImageBytes, Set<OcrFieldType> expectedFieldTypes) {
        try {
            byte[] preprocessedBytes = imagePreprocessor.enhanceContrast(originalImageBytes);
            return recognizeAndParse(preprocessedBytes, PREPROCESSED_IMAGE_FORMAT, expectedFieldTypes);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<OcrFieldExtraction> mergeFillingMissingFields(
            List<OcrFieldExtraction> primary, List<OcrFieldExtraction> retry) {
        Map<OcrFieldType, OcrFieldExtraction> merged = new EnumMap<>(OcrFieldType.class);
        primary.forEach(extraction -> merged.put(extraction.fieldType(), extraction));
        retry.forEach(extraction -> merged.putIfAbsent(extraction.fieldType(), extraction));
        return List.copyOf(merged.values());
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
