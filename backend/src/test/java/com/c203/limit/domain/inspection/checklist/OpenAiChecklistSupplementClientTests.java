package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.OsFamily;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiChecklistSupplementClientTests {

    private static final String ENDPOINT = "https://api.openai.com/v1/responses";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesStrictOutputAndRejectsNonOfficialDomains() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OpenAiChecklistSupplementClient client = new OpenAiChecklistSupplementClient(
                RestClient.builder(),
                mapper,
                new DeviceChecklistFeatureCatalog(),
                "https://api.openai.com/v1/responses",
                "test-key",
                "gpt-5.6-luna",
                "samsung.com");
        String output = mapper.writeValueAsString(mapper.readTree(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "CAMERA",
                      "evidenceStatus": "VERIFIED",
                      "reason": "official specification",
                      "checkGuide": "open the camera app",
                      "sourceUrl": "https://www.samsung.com/sec/support/model/NT960/",
                      "sourceTitle": "Samsung support"
                    },
                    {
                      "featureCode": "OLED",
                      "evidenceStatus": "LIKELY",
                      "reason": "untrusted",
                      "checkGuide": "check the display",
                      "sourceUrl": "https://example.com/product",
                      "sourceTitle": "Other"
                    }
                  ],
                  "reviewCandidates": ["ambient light sensor"]
                }
                """));
        String response = mapper.writeValueAsString(java.util.Map.of("output_text", output));

        ChecklistSupplementResult result = client.parse(response);

        assertThat(result.available()).isTrue();
        assertThat(result.suggestions())
                .extracting(ChecklistSuggestion::featureCode)
                .containsExactly("CAMERA");
        assertThat(result.suggestions())
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.reason()).contains("제조사 공식 자료");
                    assertThat(suggestion.checkGuide()).isEqualTo(
                            LaptopFeatureCode.CAMERA.defaultCheckGuideKo());
                });
        assertThat(result.reviewCandidates()).containsExactly("ambient light sensor");
    }

    @Test
    void suggestReturnsConfigurationUnavailableWhenApiKeyIsBlank() {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "   ", "samsung.com");

        ChecklistSupplementResult result = client.suggest(laptopContext());

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_CONFIGURATION_UNAVAILABLE");
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void suggestReturnsConfigurationUnavailableWhenNoAllowedDomainIsConfigured() {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", " , , ");

        ChecklistSupplementResult result = client.suggest(laptopContext());

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_CONFIGURATION_UNAVAILABLE");
    }

    @Test
    void suggestPostsStrictSchemaRequestAndReadsNestedOutputText() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiChecklistSupplementClient client = client(builder, "test-key", "samsung.com");
        String outputText = objectMapper.writeValueAsString(objectMapper.readTree(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "OLED",
                      "evidenceStatus": "VERIFIED",
                      "reason": "공식 지원 페이지에서 OLED 패널 탑재가 확인됩니다.",
                      "checkGuide": "회색 단색 화면을 띄워 번인 여부를 확인하세요.",
                      "sourceUrl": "https://www.samsung.com/sec/support/model/NT960/",
                      "sourceTitle": "삼성 지원"
                    }
                  ],
                  "reviewCandidates": ["지문 인식 전원 버튼"]
                }
                """));
        String body = objectMapper.writeValueAsString(Map.of(
                "output",
                List.of(
                        Map.of("type", "reasoning", "content", List.of()),
                        Map.of(
                                "type",
                                "message",
                                "content",
                                List.of(Map.of("type", "output_text", "text", outputText))))));

        server.expect(requestTo(ENDPOINT))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.max_tool_calls").value(2))
                .andExpect(jsonPath("$.tools[0].type").value("web_search"))
                .andExpect(jsonPath("$.tools[0].filters.allowed_domains[0]").value("samsung.com"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.properties.suggestions.maxItems")
                        .value(DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ChecklistSupplementResult result = client.suggest(laptopContext());

        assertThat(result.available()).isTrue();
        assertThat(result.suggestions())
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.featureCode()).isEqualTo("OLED");
                    assertThat(suggestion.featureName())
                            .isEqualTo(LaptopFeatureCode.OLED.displayNameKo());
                    assertThat(suggestion.evidenceStatus())
                            .isEqualTo(ChecklistEvidenceStatus.VERIFIED);
                    assertThat(suggestion.reason())
                            .isEqualTo("공식 지원 페이지에서 OLED 패널 탑재가 확인됩니다.");
                    assertThat(suggestion.checkGuide())
                            .isEqualTo("회색 단색 화면을 띄워 번인 여부를 확인하세요.");
                    assertThat(suggestion.sourceTitle()).isEqualTo("삼성 지원");
                });
        assertThat(result.reviewCandidates()).containsExactly("지문 인식 전원 버튼");
        server.verify();
    }

    @Test
    void suggestReturnsRequestFailedWhenEndpointRespondsWithServerError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiChecklistSupplementClient client = client(builder, "test-key", "samsung.com");

        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        ChecklistSupplementResult result = client.suggest(laptopContext());

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_REQUEST_FAILED");
        server.verify();
    }

    @Test
    void parseReturnsInvalidResponseWhenBodyIsMissing() {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        assertThat(client.parse(null).failureCode()).isEqualTo("AI_RESPONSE_INVALID");
        assertThat(client.parse("   ").failureCode()).isEqualTo("AI_RESPONSE_INVALID");
        assertThat(client.parse(null).available()).isFalse();
    }

    @Test
    void parseReturnsInvalidResponseWhenNoOutputTextContentExists() {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(
                """
                {
                  "output": [
                    {"content": [{"type": "reasoning", "text": "thinking"}]},
                    {"content": [{"type": "output_text", "text": 42}]}
                  ]
                }
                """);

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_RESPONSE_INVALID");
    }

    @Test
    void parseReturnsInvalidResponseWhenOutputTextIsNotJson() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");
        String body = objectMapper.writeValueAsString(Map.of("output_text", "not-json{"));

        assertThat(client.parse(body).failureCode()).isEqualTo("AI_RESPONSE_INVALID");
    }

    @Test
    void parseReturnsInvalidResponseWhenFeatureCodeIsOutsideTheCatalog() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(responseWith(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "TELEPORT",
                      "evidenceStatus": "VERIFIED",
                      "reason": "관련 없는 코드",
                      "checkGuide": "확인",
                      "sourceUrl": "https://www.samsung.com/a",
                      "sourceTitle": "삼성"
                    }
                  ],
                  "reviewCandidates": []
                }
                """));

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_RESPONSE_INVALID");
    }

    @Test
    void parseReturnsInvalidResponseWhenEvidenceStatusIsUnknown() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(responseWith(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "CAMERA",
                      "evidenceStatus": "MAYBE",
                      "reason": "알 수 없는 상태",
                      "checkGuide": "확인",
                      "sourceUrl": "https://www.samsung.com/a",
                      "sourceTitle": "삼성"
                    }
                  ],
                  "reviewCandidates": []
                }
                """));

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_RESPONSE_INVALID");
    }

    @Test
    void parseAcceptsOnlyHttpsSourcesOnAllowedDomains() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(responseWith(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "CAMERA",
                      "evidenceStatus": "VERIFIED",
                      "reason": "서브 도메인 허용",
                      "checkGuide": "확인",
                      "sourceUrl": "https://www.samsung.com/a",
                      "sourceTitle": "삼성"
                    },
                    {
                      "featureCode": "WIFI",
                      "evidenceStatus": "VERIFIED",
                      "reason": "정확히 일치하는 도메인",
                      "checkGuide": "확인",
                      "sourceUrl": "https://samsung.com/b",
                      "sourceTitle": "삼성"
                    },
                    {
                      "featureCode": "OLED",
                      "evidenceStatus": "VERIFIED",
                      "reason": "http 는 거부",
                      "checkGuide": "확인",
                      "sourceUrl": "http://www.samsung.com/c",
                      "sourceTitle": "삼성"
                    },
                    {
                      "featureCode": "NUMPAD",
                      "evidenceStatus": "VERIFIED",
                      "reason": "유사 도메인은 거부",
                      "checkGuide": "확인",
                      "sourceUrl": "https://notsamsung.com/d",
                      "sourceTitle": "위장"
                    },
                    {
                      "featureCode": "SD_CARD",
                      "evidenceStatus": "VERIFIED",
                      "reason": "호스트가 없으면 거부",
                      "checkGuide": "확인",
                      "sourceUrl": "https:relative-only",
                      "sourceTitle": "없음"
                    },
                    {
                      "featureCode": "THUNDERBOLT",
                      "evidenceStatus": "VERIFIED",
                      "reason": "잘못된 URI 는 거부",
                      "checkGuide": "확인",
                      "sourceUrl": "https://bad host.samsung.com/e",
                      "sourceTitle": "잘못됨"
                    }
                  ],
                  "reviewCandidates": []
                }
                """));

        assertThat(result.available()).isTrue();
        assertThat(result.suggestions())
                .extracting(ChecklistSuggestion::featureCode)
                .containsExactly("CAMERA", "WIFI");
    }

    @Test
    void parseKeepsKoreanReasonAndGuideAfterTrimming() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(responseWith(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "CAMERA",
                      "evidenceStatus": "LIKELY",
                      "reason": "  공식 지원 페이지 기준 내장 카메라가 확인됩니다.  ",
                      "checkGuide": "  카메라 앱을 열어 영상 출력을 확인하세요.  ",
                      "sourceUrl": "https://www.samsung.com/a",
                      "sourceTitle": "삼성"
                    }
                  ],
                  "reviewCandidates": []
                }
                """));

        assertThat(result.suggestions())
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.reason())
                            .isEqualTo("공식 지원 페이지 기준 내장 카메라가 확인됩니다.");
                    assertThat(suggestion.checkGuide())
                            .isEqualTo("카메라 앱을 열어 영상 출력을 확인하세요.");
                    assertThat(suggestion.evidenceStatus())
                            .isEqualTo(ChecklistEvidenceStatus.LIKELY);
                });
    }

    @Test
    void parseUsesDeviceTypeSpecificCatalogForNonLaptopDevices() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(
                responseWith(
                        """
                        {
                          "suggestions": [
                            {
                              "featureCode": "NFC",
                              "evidenceStatus": "VERIFIED",
                              "reason": "official product page",
                              "checkGuide": "turn on NFC",
                              "sourceUrl": "https://www.samsung.com/a",
                              "sourceTitle": "Samsung"
                            }
                          ],
                          "reviewCandidates": []
                        }
                        """),
                DeviceType.SMARTPHONE);

        assertThat(result.suggestions())
                .singleElement()
                .satisfies(suggestion -> {
                    assertThat(suggestion.featureCode()).isEqualTo("NFC");
                    assertThat(suggestion.reason()).contains("제조사 공식 자료");
                    assertThat(suggestion.checkGuide()).contains("NFC 태그 인식");
                });
    }

    @Test
    void parseRejectsSmartphoneOnlyFeatureCodeWhenDeviceTypeIsLaptop() throws Exception {
        OpenAiChecklistSupplementClient client =
                client(RestClient.builder(), "test-key", "samsung.com");

        ChecklistSupplementResult result = client.parse(responseWith(
                """
                {
                  "suggestions": [
                    {
                      "featureCode": "WIRELESS_CHARGING",
                      "evidenceStatus": "VERIFIED",
                      "reason": "노트북 카탈로그에는 없는 코드",
                      "checkGuide": "확인",
                      "sourceUrl": "https://www.samsung.com/a",
                      "sourceTitle": "삼성"
                    }
                  ],
                  "reviewCandidates": []
                }
                """));

        assertThat(result.available()).isFalse();
        assertThat(result.failureCode()).isEqualTo("AI_RESPONSE_INVALID");
    }

    private OpenAiChecklistSupplementClient client(
            RestClient.Builder builder, String apiKey, String allowedDomains) {
        return new OpenAiChecklistSupplementClient(
                builder,
                objectMapper,
                new DeviceChecklistFeatureCatalog(),
                ENDPOINT,
                apiKey,
                "gpt-5.6-luna",
                allowedDomains);
    }

    private String responseWith(String outputJson) throws Exception {
        String outputText = objectMapper.writeValueAsString(objectMapper.readTree(outputJson));
        return objectMapper.writeValueAsString(Map.of("output_text", outputText));
    }

    private ChecklistGenerationContext laptopContext() {
        return new ChecklistGenerationContext(
                201L,
                DeviceType.LAPTOP,
                "Samsung",
                "Galaxy Book4",
                "NT960",
                OsFamily.WINDOWS,
                Set.of("WIFI"));
    }
}
