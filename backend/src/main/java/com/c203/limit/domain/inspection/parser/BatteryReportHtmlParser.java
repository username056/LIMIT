package com.c203.limit.domain.inspection.parser;

import com.c203.limit.domain.inspection.util.UnitNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * Windows {@code powercfg /batteryreport} HTML 출력을 Jsoup으로 파싱해 배터리 제조사·설계 용량·완전충전 용량·사이클
 * 수를 추출하고 완전충전/설계 용량 비율을 계산한다.
 */
@Component
public class BatteryReportHtmlParser {

    public BatteryReportParseResult parse(byte[] htmlBytes) {
        Document document = parseDocument(htmlBytes);

        String manufacturer = findLabelValue(document, "MANUFACTURER");
        String designCapacity = findLabelValue(document, "DESIGN CAPACITY");
        String fullChargeCapacity = findLabelValue(document, "FULL CHARGE CAPACITY");
        Integer cycleCount = parseInteger(findLabelValue(document, "CYCLE COUNT"));
        BigDecimal capacityRatio = calculateCapacityRatio(designCapacity, fullChargeCapacity);

        return new BatteryReportParseResult(
                manufacturer, designCapacity, fullChargeCapacity, cycleCount, capacityRatio);
    }

    private Document parseDocument(byte[] htmlBytes) {
        try {
            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            if (!html.isEmpty() && html.charAt(0) == '﻿') {
                html = html.substring(1);
            }
            return Jsoup.parse(html);
        } catch (Exception exception) {
            throw new BatteryReportParseException("Failed to parse battery report HTML", exception);
        }
    }

    private String findLabelValue(Document document, String labelText) {
        Elements labelSpans = document.select("span.label");
        for (Element span : labelSpans) {
            if (!labelText.equals(span.text().trim())) {
                continue;
            }
            Element row = span.closest("tr");
            if (row == null) {
                continue;
            }
            Elements cells = row.select("> td");
            if (cells.size() < 2) {
                continue;
            }
            String value = cells.get(1).text();
            return value.isBlank() ? null : value.trim();
        }
        return null;
    }

    private Integer parseInteger(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : Integer.valueOf(digits);
    }

    private BigDecimal calculateCapacityRatio(String designCapacityRaw, String fullChargeCapacityRaw) {
        BigDecimal design = UnitNormalizer.extractMilliwattHours(designCapacityRaw);
        BigDecimal fullCharge = UnitNormalizer.extractMilliwattHours(fullChargeCapacityRaw);
        if (design == null || fullCharge == null || design.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal calculatedRatio = fullCharge
                .divide(design, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return calculatedRatio.min(BigDecimal.valueOf(100).setScale(2));
    }
}
