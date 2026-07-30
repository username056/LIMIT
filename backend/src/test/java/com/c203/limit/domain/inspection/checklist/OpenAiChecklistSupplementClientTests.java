package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiChecklistSupplementClientTests {

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
}
