package com.c203.limit.domain.inspection.client;

import com.c203.limit.domain.inspection.config.NaverClovaOcrProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 증거 이미지를 내려받아 네이버 클로바 General OCR로 텍스트를 인식한다. */
@Component
public class NaverClovaOcrClient {

    private static final String SECRET_HEADER = "X-OCR-SECRET";
    private static final String INFER_SUCCESS = "SUCCESS";

    private final RestClient restClient;
    private final NaverClovaOcrProperties properties;

    public NaverClovaOcrClient(
            @Qualifier("naverClovaOcrRestClientBuilder") RestClient.Builder restClientBuilder,
            NaverClovaOcrProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public NaverClovaOcrResult recognize(String imageUrl, String format) {
        byte[] imageBytes = fetchImage(imageUrl);
        NaverClovaOcrResponse response = requestOcr(imageBytes, format);
        return toResult(response);
    }

    private byte[] fetchImage(String imageUrl) {
        byte[] bytes;
        try {
            bytes = restClient.get().uri(imageUrl).retrieve().body(byte[].class);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OCR_IMAGE_FETCH_FAILED);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.OCR_IMAGE_FETCH_FAILED);
        }
        return bytes;
    }

    private NaverClovaOcrResponse requestOcr(byte[] imageBytes, String format) {
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);
        NaverClovaOcrRequest request =
                NaverClovaOcrRequest.of(UUID.randomUUID().toString(), format, base64Data);
        try {
            return restClient
                    .post()
                    .uri(properties.getInvokeUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SECRET_HEADER, properties.getSecretKey())
                    .body(request)
                    .retrieve()
                    .body(NaverClovaOcrResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.OCR_REQUEST_FAILED);
        }
    }

    private NaverClovaOcrResult toResult(NaverClovaOcrResponse response) {
        if (response == null || response.images() == null || response.images().isEmpty()) {
            throw new BusinessException(ErrorCode.OCR_RECOGNITION_FAILED);
        }
        NaverClovaOcrResponse.ImageResult imageResult = response.images().get(0);
        List<NaverClovaOcrResponse.Field> fields = imageResult.fields();
        if (!INFER_SUCCESS.equals(imageResult.inferResult()) || fields == null || fields.isEmpty()) {
            throw new BusinessException(ErrorCode.OCR_RECOGNITION_FAILED);
        }

        String rawText =
                fields.stream()
                        .map(NaverClovaOcrResponse.Field::inferText)
                        .collect(Collectors.joining("\n"));
        BigDecimal confidence = averageConfidence(fields);
        String modelVersion = response.version() != null ? response.version() : "V2";

        return new NaverClovaOcrResult(rawText, confidence, modelVersion);
    }

    private BigDecimal averageConfidence(List<NaverClovaOcrResponse.Field> fields) {
        BigDecimal sum =
                fields.stream()
                        .map(NaverClovaOcrResponse.Field::inferConfidence)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(fields.size()), 3, RoundingMode.HALF_UP);
    }
}
