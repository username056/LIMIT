package com.c203.limit.domain.inspection.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record NaverClovaOcrResponse(String version, List<ImageResult> images) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImageResult(String inferResult, String message, List<Field> fields) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Field(
            String inferText, BigDecimal inferConfidence, Boolean lineBreak, BoundingPoly boundingPoly) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BoundingPoly(List<Vertex> vertices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Vertex(double x, double y) {}
}
