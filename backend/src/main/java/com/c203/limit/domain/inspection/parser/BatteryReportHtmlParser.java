package com.c203.limit.domain.inspection.parser;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Windows {@code powercfg /batteryreport} XHTML 출력을 DOM으로 파싱해 배터리 제조사·설계 용량·완전충전
 * 용량·사이클 수를 추출하고 완전충전/설계 용량 비율을 계산한다.
 */
@Component
public class BatteryReportHtmlParser {

    /**
     * powercfg 리포트의 script/style 블록에는 XML 텍스트로는 유효하지 않은 JS/CSS(비교 연산자 등)가
     * 그대로 들어있어 DOM 파싱이 깨진다. 우리가 필요한 정보(라벨/값 테이블)는 그 밖에 있으므로
     * 파싱 전에 script/style 블록만 제거한다.
     */
    private static final Pattern SCRIPT_OR_STYLE_BLOCK =
            Pattern.compile("<(script|style)\\b[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public BatteryReportParseResult parse(byte[] htmlBytes) {
        Element root = parseDocument(htmlBytes);

        String manufacturer = findLabelValue(root, "MANUFACTURER");
        String designCapacity = findLabelValue(root, "DESIGN CAPACITY");
        String fullChargeCapacity = findLabelValue(root, "FULL CHARGE CAPACITY");
        Integer cycleCount = parseInteger(findLabelValue(root, "CYCLE COUNT"));
        BigDecimal capacityRatio = calculateCapacityRatio(designCapacity, fullChargeCapacity);

        return new BatteryReportParseResult(
                manufacturer, designCapacity, fullChargeCapacity, cycleCount, capacityRatio);
    }

    private Element parseDocument(byte[] htmlBytes) {
        try {
            String html = new String(htmlBytes, StandardCharsets.UTF_8);
            if (!html.isEmpty() && html.charAt(0) == '﻿') {
                html = html.substring(1);
            }
            html = SCRIPT_OR_STYLE_BLOCK.matcher(html).replaceAll("");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 배터리 리포트는 <!DOCTYPE html> 선언을 포함하므로 DOCTYPE 자체는 허용하되
            // 외부 엔티티·DTD·스키마 해석은 모두 차단해 XXE를 방지한다.
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Document document = builder.parse(new InputSource(new StringReader(html)));
            document.getDocumentElement().normalize();
            return document.getDocumentElement();
        } catch (Exception exception) {
            throw new BatteryReportParseException("Failed to parse battery report HTML", exception);
        }
    }

    private String findLabelValue(Element root, String labelText) {
        NodeList spans = root.getElementsByTagName("span");
        for (int i = 0; i < spans.getLength(); i++) {
            Element span = (Element) spans.item(i);
            if ("label".equals(span.getAttribute("class")) && labelText.equals(text(span))) {
                Node valueNode = nextElementSibling(span.getParentNode());
                if (valueNode != null) {
                    String value = valueNode.getTextContent();
                    return value == null || value.isBlank() ? null : value.trim();
                }
            }
        }
        return null;
    }

    private Node nextElementSibling(Node node) {
        Node sibling = node == null ? null : node.getNextSibling();
        while (sibling != null && sibling.getNodeType() != Node.ELEMENT_NODE) {
            sibling = sibling.getNextSibling();
        }
        return sibling;
    }

    private String text(Element element) {
        String value = element.getTextContent();
        return value == null ? "" : value.trim();
    }

    private Integer parseInteger(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : Integer.valueOf(digits);
    }

    private BigDecimal calculateCapacityRatio(String designCapacityRaw, String fullChargeCapacityRaw) {
        BigDecimal design = parseMilliwattHours(designCapacityRaw);
        BigDecimal fullCharge = parseMilliwattHours(fullChargeCapacityRaw);
        if (design == null || fullCharge == null || design.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return fullCharge
                .divide(design, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseMilliwattHours(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : new BigDecimal(digits);
    }
}
