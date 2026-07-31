package com.c203.limit.domain.inspection.client;

import com.c203.limit.domain.inspection.config.NaverClovaOcrProperties;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 증거 이미지를 내려받아 네이버 클로바 General OCR로 텍스트를 인식한다. */
@Component
public class NaverClovaOcrClient {

    private static final Logger log = LoggerFactory.getLogger(NaverClovaOcrClient.class);
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
        return toResult(fetchAndRecognize(imageUrl, format));
    }

    /**
     * 이미지 안의 텍스트 조각들을 클로바가 반환한 순서와 좌표 그대로 반환한다(필드별 구조화는 호출자가 담당).
     * 좌표는 라벨과 값이 텍스트 스트림 상에서 멀리 떨어진 카드형·표 레이아웃을 구조화할 때 필요하다.
     */
    public List<OcrToken> recognizeFields(String imageUrl, String format) {
        return recognizeFields(fetchImage(imageUrl), format);
    }

    /** 이미 내려받은(또는 전처리한) 이미지 바이트로 곧바로 인식한다 — 실패 시 재시도 등 재요청용. */
    public List<OcrToken> recognizeFields(byte[] imageBytes, String format) {
        NaverClovaOcrResponse.ImageResult imageResult = validImageResult(requestOcr(imageBytes, format));
        return imageResult.fields().stream().map(this::toToken).toList();
    }

    private OcrToken toToken(NaverClovaOcrResponse.Field field) {
        BigDecimal confidence = field.inferConfidence() == null ? BigDecimal.ZERO : field.inferConfidence();
        double[] box = boundingBox(field.boundingPoly());
        return new OcrToken(field.inferText(), confidence, box[0], box[1], box[2], box[3]);
    }

    /** boundingPoly의 꼭짓점들을 감싸는 최소 사각형(left, top, right, bottom)을 계산한다. */
    private double[] boundingBox(NaverClovaOcrResponse.BoundingPoly boundingPoly) {
        if (boundingPoly == null || boundingPoly.vertices() == null || boundingPoly.vertices().isEmpty()) {
            return new double[] {0, 0, 0, 0};
        }
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = -Double.MAX_VALUE;
        double bottom = -Double.MAX_VALUE;
        for (NaverClovaOcrResponse.Vertex vertex : boundingPoly.vertices()) {
            left = Math.min(left, vertex.x());
            top = Math.min(top, vertex.y());
            right = Math.max(right, vertex.x());
            bottom = Math.max(bottom, vertex.y());
        }
        return new double[] {left, top, right, bottom};
    }

    private NaverClovaOcrResponse fetchAndRecognize(String imageUrl, String format) {
        byte[] imageBytes = fetchImage(imageUrl);
        return requestOcr(imageBytes, format);
    }

    /** evidence의 cdnUrl에서 이미지 바이트를 내려받는다. 재시도용 전처리 전 원본 바이트를 얻을 때도 쓴다. */
    public byte[] fetchImage(String imageUrl) {
        byte[] bytes;
        try {
            bytes = restClient.get().uri(URI.create(imageUrl)).retrieve().body(byte[].class);
        } catch (RestClientException exception) {
            log.warn("evidence image fetch failed: imageUrl={}", imageUrl, exception);
            throw new BusinessException(ErrorCode.OCR_IMAGE_FETCH_FAILED);
        }
        if (bytes == null || bytes.length == 0) {
            log.warn("evidence image fetch returned empty body: imageUrl={}", imageUrl);
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
        NaverClovaOcrResponse.ImageResult imageResult = validImageResult(response);
        List<NaverClovaOcrResponse.Field> fields = imageResult.fields();

        String rawText =
                fields.stream()
                        .map(NaverClovaOcrResponse.Field::inferText)
                        .collect(Collectors.joining("\n"));
        BigDecimal confidence = averageConfidence(fields);
        String modelVersion = response.version() != null ? response.version() : "V2";

        return new NaverClovaOcrResult(rawText, confidence, modelVersion);
    }

    private NaverClovaOcrResponse.ImageResult validImageResult(NaverClovaOcrResponse response) {
        if (response == null || response.images() == null || response.images().isEmpty()) {
            throw new BusinessException(ErrorCode.OCR_RECOGNITION_FAILED);
        }
        NaverClovaOcrResponse.ImageResult imageResult = response.images().get(0);
        List<NaverClovaOcrResponse.Field> fields = imageResult.fields();
        if (!INFER_SUCCESS.equals(imageResult.inferResult()) || fields == null || fields.isEmpty()) {
            throw new BusinessException(ErrorCode.OCR_RECOGNITION_FAILED);
        }
        return imageResult;
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
