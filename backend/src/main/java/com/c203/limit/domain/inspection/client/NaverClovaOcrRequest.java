package com.c203.limit.domain.inspection.client;

import java.util.List;

record NaverClovaOcrRequest(String version, String requestId, long timestamp, List<Image> images) {

    record Image(String format, String name, String data) {}

    static NaverClovaOcrRequest of(String requestId, String format, String base64Data) {
        return new NaverClovaOcrRequest(
                "V2",
                requestId,
                System.currentTimeMillis(),
                List.of(new Image(format, "image", base64Data)));
    }
}
