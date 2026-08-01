package com.c203.limit.domain.product.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 매물 등록 시점의 사양 스냅샷.
 *
 * <p>카탈로그(device_model/device_variant)는 오타 수정·사양 보강으로 계속 바뀐다. 매물이 그 행을
 * FK로만 참조하면 "이 매물은 어떤 사양으로 팔렸는가"라는 질문에 나중에 답할 수 없다. 등록 순간의
 * 값을 JSON으로 얼려 listing.spec_snapshot에 남긴다.
 *
 * <p>Spring 빈이 아니라 정적 팩토리다. 외부 상태가 없는 값 변환이라 주입할 것이 없고, 생성자
 * 시그니처를 늘리지 않아 기존 호출부와 테스트를 건드리지 않는다.
 */
public final class ListingSpecSnapshot {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ListingSpecSnapshot() {}

    /**
     * null인 항목은 키 자체를 넣지 않는다. {@code "cpu": null}을 남기면 '값이 없다'와 '해당 축이
     * 이 기기에 없다'가 구분되지 않는다. 노트북에만 있는 축이 스마트폰 스냅샷에 빈 값으로 남는
     * 상황을 피한다.
     */
    public static String of(
            String manufacturer,
            String modelName,
            String modelCode,
            String color,
            Integer storageGb,
            Integer memoryGb,
            BigDecimal screenSizeInches,
            String cpu,
            String gpu,
            String connectivity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        put(snapshot, "manufacturer", manufacturer);
        put(snapshot, "modelName", modelName);
        put(snapshot, "modelCode", modelCode);
        put(snapshot, "screenSizeInches", screenSizeInches);
        put(snapshot, "cpu", cpu);
        put(snapshot, "gpu", gpu);
        put(snapshot, "memoryGb", memoryGb);
        put(snapshot, "storageGb", storageGb);
        put(snapshot, "connectivity", connectivity);
        put(snapshot, "color", color);
        if (snapshot.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            // 값이 String/Integer/BigDecimal뿐이라 실제로는 도달할 수 없는 분기다. 조용히 null을
            // 돌려주면 스냅샷이 비어 있는 원인을 나중에 추적할 수 없으므로 그대로 드러낸다.
            throw new IllegalStateException("failed to serialize listing spec snapshot", exception);
        }
    }

    private static void put(Map<String, Object> snapshot, String key, Object value) {
        if (value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        snapshot.put(key, value);
    }
}
