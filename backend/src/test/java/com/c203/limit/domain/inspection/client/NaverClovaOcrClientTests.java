package com.c203.limit.domain.inspection.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.c203.limit.domain.inspection.config.NaverClovaOcrProperties;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverClovaOcrClientTests {

    private static final String IMAGE_URL = "https://cdn.example.com/evidence/1.jpg";
    private static final String INVOKE_URL =
            "https://naveropenapi.apigw.ntruss.com/vision/v1/ocr-test";

    @Test
    void recognizeAggregatesTextAndAverageConfidenceOnSuccess() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverClovaOcrClient(builder, properties());

        server.expect(requestTo(IMAGE_URL))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo(INVOKE_URL))
                .andExpect(header("X-OCR-SECRET", "secret-key"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "version": "V2",
                                  "images": [
                                    {
                                      "inferResult": "SUCCESS",
                                      "message": "SUCCESS",
                                      "fields": [
                                        {"inferText": "Galaxy", "inferConfidence": 0.98},
                                        {"inferText": "Book4 Pro", "inferConfidence": 0.95}
                                      ]
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        NaverClovaOcrResult result = client.recognize(IMAGE_URL, "jpg");

        assertThat(result.rawText()).isEqualTo("Galaxy\nBook4 Pro");
        assertThat(result.confidence()).isEqualByComparingTo("0.965");
        assertThat(result.modelVersion()).isEqualTo("V2");
        server.verify();
    }

    @Test
    void recognizeFieldsReturnsTokensInReadingOrder() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverClovaOcrClient(builder, properties());

        server.expect(requestTo(IMAGE_URL))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo(INVOKE_URL))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "version": "V2",
                                  "images": [
                                    {
                                      "inferResult": "SUCCESS",
                                      "message": "SUCCESS",
                                      "fields": [
                                        {
                                          "inferText": "저장소",
                                          "inferConfidence": 0.99,
                                          "boundingPoly": {
                                            "vertices": [
                                              {"x": 20, "y": 40}, {"x": 80, "y": 40},
                                              {"x": 80, "y": 60}, {"x": 20, "y": 60}
                                            ]
                                          }
                                        },
                                        {
                                          "inferText": "954",
                                          "inferConfidence": 0.95,
                                          "boundingPoly": {
                                            "vertices": [
                                              {"x": 20, "y": 90}, {"x": 50, "y": 90},
                                              {"x": 50, "y": 120}, {"x": 20, "y": 120}
                                            ]
                                          }
                                        },
                                        {
                                          "inferText": "GB",
                                          "inferConfidence": 0.97,
                                          "boundingPoly": {
                                            "vertices": [
                                              {"x": 55, "y": 90}, {"x": 80, "y": 90},
                                              {"x": 80, "y": 120}, {"x": 55, "y": 120}
                                            ]
                                          }
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        List<OcrToken> tokens = client.recognizeFields(IMAGE_URL, "jpg");

        assertThat(tokens).extracting(OcrToken::text).containsExactly("저장소", "954", "GB");
        assertThat(tokens.get(0).left()).isEqualTo(20.0);
        assertThat(tokens.get(0).top()).isEqualTo(40.0);
        assertThat(tokens.get(0).right()).isEqualTo(80.0);
        assertThat(tokens.get(0).bottom()).isEqualTo(60.0);
        server.verify();
    }

    @Test
    void recognizeThrowsWhenInferResultIsNotSuccess() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverClovaOcrClient(builder, properties());

        server.expect(requestTo(IMAGE_URL))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo(INVOKE_URL))
                .andRespond(
                        withSuccess(
                                """
                                {"version": "V2", "images": [{"inferResult": "FAILURE", "fields": []}]}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.recognize(IMAGE_URL, "jpg"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_RECOGNITION_FAILED));
        server.verify();
    }

    @Test
    void recognizeThrowsOcrRequestFailedWhenClovaReturnsServerError() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverClovaOcrClient(builder, properties());

        server.expect(requestTo(IMAGE_URL))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));
        server.expect(requestTo(INVOKE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.recognize(IMAGE_URL, "jpg"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_REQUEST_FAILED));
        server.verify();
    }

    @Test
    void recognizeThrowsOcrImageFetchFailedWhenImageDownloadFails() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NaverClovaOcrClient(builder, properties());

        server.expect(requestTo(IMAGE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.recognize(IMAGE_URL, "jpg"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OCR_IMAGE_FETCH_FAILED));
        server.verify();
    }

    private NaverClovaOcrProperties properties() {
        var properties = new NaverClovaOcrProperties();
        properties.setInvokeUrl(INVOKE_URL);
        properties.setSecretKey("secret-key");
        return properties;
    }
}
