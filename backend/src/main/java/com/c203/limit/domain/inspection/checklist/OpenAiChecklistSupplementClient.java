package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(
        prefix = "limit.ai.checklist",
        name = "enabled",
        havingValue = "true")
public class OpenAiChecklistSupplementClient implements ChecklistSupplementClient {
    private static final Logger log =
            LoggerFactory.getLogger(OpenAiChecklistSupplementClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DeviceChecklistFeatureCatalog featureCatalog;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final List<String> allowedDomains;

    public OpenAiChecklistSupplementClient(
            @Qualifier("checklistAiRestClientBuilder") RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DeviceChecklistFeatureCatalog featureCatalog,
            @Value("${limit.ai.checklist.endpoint:https://api.openai.com/v1/responses}")
                    String endpoint,
            @Value("${limit.ai.checklist.api-key:}") String apiKey,
            @Value("${limit.ai.checklist.model:gpt-5.6-luna}") String model,
            @Value("${limit.ai.checklist.allowed-domains:samsung.com,apple.com,google.com,"
                            + "lg.com,microsoft.com,lenovo.com,dell.com,hp.com,asus.com,"
                            + "acer.com,msi.com}")
                    String allowedDomains) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.featureCatalog = featureCatalog;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.allowedDomains = Arrays.stream(allowedDomains.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public ChecklistSupplementResult suggest(ChecklistGenerationContext context) {
        if (apiKey.isBlank() || allowedDomains.isEmpty()) {
            return ChecklistSupplementResult.unavailable();
        }
        try {
            String response = restClient
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody(context))
                    .retrieve()
                    .body(String.class);
            return parse(response, context.deviceType());
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("AI checklist supplement unavailable: {}", exception.getClass().getSimpleName());
            return ChecklistSupplementResult.unavailable();
        }
    }

    ChecklistSupplementResult parse(String responseBody) {
        return parse(responseBody, DeviceType.LAPTOP);
    }

    ChecklistSupplementResult parse(String responseBody, DeviceType deviceType) {
        if (responseBody == null || responseBody.isBlank()) {
            return ChecklistSupplementResult.unavailable();
        }
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            String outputText = extractOutputText(response);
            if (outputText == null) {
                return ChecklistSupplementResult.unavailable();
            }
            JsonNode result = objectMapper.readTree(outputText);
            List<ChecklistSuggestion> suggestions = new ArrayList<>();
            for (JsonNode node : result.path("suggestions")) {
                DeviceChecklistFeatureCatalog.FeatureDefinition definition =
                        featureCatalog
                                .find(deviceType, node.path("featureCode").asText())
                                .orElseThrow();
                ChecklistEvidenceStatus status =
                        ChecklistEvidenceStatus.valueOf(node.path("evidenceStatus").asText());
                String sourceUrl = node.path("sourceUrl").asText();
                if (isAllowedSource(sourceUrl)) {
                    String reason = koreanOrFallback(
                            node.path("reason").asText(),
                            "제조사 공식 자료에서 %s 지원이 확인되어 실제 기기의 동작 여부를 확인해야 합니다."
                                    .formatted(definition.displayName()));
                    String checkGuide = koreanOrFallback(
                            node.path("checkGuide").asText(), definition.checkGuide());
                    suggestions.add(new ChecklistSuggestion(
                            definition.code(),
                            definition.displayName(),
                            status,
                            reason,
                            checkGuide,
                            sourceUrl,
                            node.path("sourceTitle").asText()));
                }
            }
            List<String> reviewCandidates = new ArrayList<>();
            result.path("reviewCandidates").forEach(node -> reviewCandidates.add(node.asText()));
            return new ChecklistSupplementResult(true, suggestions, reviewCandidates);
        } catch (Exception exception) {
            log.warn("AI checklist response rejected: {}", exception.getClass().getSimpleName());
            return ChecklistSupplementResult.unavailable();
        }
    }

    private Map<String, Object> requestBody(ChecklistGenerationContext context) {
        Map<String, Object> webSearch = new LinkedHashMap<>();
        webSearch.put("type", "web_search");
        webSearch.put("search_context_size", "low");
        webSearch.put("filters", Map.of("allowed_domains", allowedDomains));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("reasoning", Map.of("effort", "low"));
        body.put("max_tool_calls", 2);
        body.put("max_output_tokens", 1800);
        body.put("tools", List.of(webSearch));
        body.put("include", List.of("web_search_call.action.sources"));
        body.put("input", prompt(context));
        body.put(
                "text",
                Map.of(
                        "format",
                        Map.of(
                                "type",
                                "json_schema",
                                "name",
                                "device_checklist_supplement",
                                "strict",
                                true,
                                "schema",
                                responseSchema(context.deviceType()))));
        return body;
    }

    private String prompt(ChecklistGenerationContext context) {
        String featureCodes = featureCatalog.definitionsFor(context.deviceType()).stream()
                .map(definition -> "%s (%s): %s"
                        .formatted(
                                definition.code(),
                                definition.displayName(),
                                definition.checkGuide()))
                .collect(Collectors.joining("\n"));
        String confirmed = String.join(", ", context.confirmedFeatures());
        return """
                You supplement a verified inspection checklist for an electronic device.
                Use only official manufacturer product/support pages returned by web search.
                Never infer from shopping sites, blogs, communities, or model-family assumptions.
                Return at most five supported special features not already confirmed.
                VERIFIED means the exact model is explicitly supported by an official source.
                LIKELY means an official source supports the model family but exact variant is unclear.
                UNKNOWN means evidence is insufficient. CONFLICTED means official sources disagree.
                Only use the approved feature codes for this device type listed below.
                Treat a feature as supported only when the source describes this exact model or
                explicitly identifies its model family.
                Write reason and checkGuide in natural Korean.
                reason must explain why this exact device feature was selected from the official source.
                checkGuide must tell a seller what physical function to test and how to verify it.
                Put an official feature outside that library into reviewCandidates as a short Korean
                user-facing feature name, never as an internal enum-style code.

                Device type: %s
                Manufacturer: %s
                Model name: %s
                Model code: %s
                OS: %s
                Already confirmed: %s
                Approved feature codes:
                %s
                """
                .formatted(
                        context.deviceType(),
                        context.manufacturer(),
                        context.modelName(),
                        context.modelCode() == null ? "unknown" : context.modelCode(),
                        context.osFamily(),
                        confirmed.isBlank() ? "none" : confirmed,
                        featureCodes);
    }

    private Map<String, Object> responseSchema(DeviceType deviceType) {
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("type", "object");
        suggestion.put("additionalProperties", false);
        suggestion.put(
                "properties",
                Map.of(
                        "featureCode",
                        Map.of(
                                "type",
                                "string",
                                "enum",
                                featureCatalog.definitionsFor(deviceType).stream()
                                        .map(DeviceChecklistFeatureCatalog.FeatureDefinition::code)
                                        .toList()),
                        "evidenceStatus",
                        Map.of(
                                "type",
                                "string",
                                "enum",
                                Arrays.stream(ChecklistEvidenceStatus.values())
                                        .map(Enum::name)
                                        .toList()),
                        "reason",
                        Map.of("type", "string"),
                        "checkGuide",
                        Map.of("type", "string"),
                        "sourceUrl",
                        Map.of("type", "string"),
                        "sourceTitle",
                        Map.of("type", "string")));
        suggestion.put(
                "required",
                List.of(
                        "featureCode",
                        "evidenceStatus",
                        "reason",
                        "checkGuide",
                        "sourceUrl",
                        "sourceTitle"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put(
                "properties",
                Map.of(
                        "suggestions",
                        Map.of(
                                "type",
                                "array",
                                "maxItems",
                                DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS,
                                "items",
                                suggestion),
                        "reviewCandidates",
                        Map.of(
                                "type",
                                "array",
                                "maxItems",
                                DeviceChecklistFeatureCatalog.MAX_ADDITIONAL_ITEMS,
                                "items",
                                Map.of("type", "string"))));
        schema.put("required", List.of("suggestions", "reviewCandidates"));
        return schema;
    }

    private String extractOutputText(JsonNode response) {
        if (response.path("output_text").isTextual()) {
            return response.path("output_text").asText();
        }
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())
                        && content.path("text").isTextual()) {
                    return content.path("text").asText();
                }
            }
        }
        return null;
    }

    private boolean isAllowedSource(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return allowedDomains.stream().anyMatch(domain ->
                    normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String koreanOrFallback(String value, String fallback) {
        if (value != null && value.codePoints().anyMatch(this::isHangulCodePoint)) {
            return value.trim();
        }
        return fallback;
    }

    private boolean isHangulCodePoint(int codePoint) {
        return (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0x1100 && codePoint <= 0x11FF)
                || (codePoint >= 0x3130 && codePoint <= 0x318F);
    }
}
