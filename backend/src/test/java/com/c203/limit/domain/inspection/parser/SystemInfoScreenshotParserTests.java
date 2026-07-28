package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import com.c203.limit.domain.inspection.service.OcrFieldExpectations;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 실제 라이브 호출(Naver Clova)에서 확인한 좌표를 그대로 사용해 파서를 검증한다. 클로바는 카드형 상단
 * 영역을 "라벨 4개 먼저(같은 세로 줄) → 값 4개(같은 세로 줄, 다른 세로 줄)" 순서로, 표 영역을 "라벨 여러
 * 개(각자 다른 세로 줄) → 값 여러 개" 순서로 반환하며, 값이 카드 폭을 넘기면 다음 줄로 줄바꿈된다(예:
 * 프로세서 모델명). 좌표가 없으면 이 순서는 텍스트만으로 구조화할 수 없다(라벨 바로 뒤에 값이 오지 않고,
 * 줄바꿈된 값은 텍스트 스트림 상 다른 행 데이터와 섞여 있기 때문).
 */
class SystemInfoScreenshotParserTests {

    private final SystemInfoScreenshotParser parser = new SystemInfoScreenshotParser();

    private static OcrToken t(String text, double confidence, double left, double top, double right, double bottom) {
        return new OcrToken(text, new BigDecimal(String.valueOf(confidence)), left, top, right, bottom);
    }

    /**
     * 실제 라이브 테스트(NaverClovaOcrClient.recognizeFields)로 확인한 좌표를 그대로 옮겼다. 프로세서
     * 모델명("13th Gen Intel(R) Core(TM) i7-13700H")이 카드 폭을 넘겨 두 줄로 줄바꿈된 것을 포함한다.
     */
    private static List<OcrToken> fullScreenshotTokens() {
        List<OcrToken> tokens = new ArrayList<>();
        // 카드 라벨 행
        tokens.add(t("저장소", 0.999, 147, 245, 224, 273));
        tokens.add(t("그래픽", 0.995, 573, 247, 645, 273));
        tokens.add(t("카드", 0.995, 654, 247, 703, 273));
        tokens.add(t("설치된", 0.999, 997, 245, 1072, 276));
        tokens.add(t("RAM", 0.999, 1075, 247, 1132, 273));
        tokens.add(t("프로세서", 0.999, 1421, 245, 1519, 273));
        // 카드 값 행 1 (저장소/그래픽카드/RAM 값 + 프로세서 값 1번째 줄)
        tokens.add(t("954", 0.507, 106, 302, 167, 334));
        tokens.add(t("GB", 0.507, 170, 302, 219, 334));
        tokens.add(t("6", 0.975, 536, 308, 553, 331));
        tokens.add(t("GB", 0.975, 559, 305, 605, 334));
        tokens.add(t("32.0GB", 0.992, 957, 302, 1066, 337));
        tokens.add(t("13th", 0.9999, 1377, 302, 1444, 331));
        tokens.add(t("Gen", 0.9998, 1450, 305, 1513, 334));
        tokens.add(t("Intel(R)", 0.996, 1516, 302, 1625, 337));
        // 프로세서 값 2번째 줄 (카드 폭을 넘겨 줄바꿈됨)
        tokens.add(t("Core(TM)", 0.999, 1380, 348, 1525, 383));
        tokens.add(t("i7-13700H", 0.9996, 1522, 348, 1674, 377));
        // 호스트명 + 라벨 없는 OEM 코드
        tokens.add(t("DESKTOP-UB20P0O", 0.989, 109, 651, 363, 677));
        tokens.add(t("960XFH", 0.996, 109, 688, 196, 709));
        // Windows 사양 표: 라벨 열
        tokens.add(t("에디션", 0.995, 193, 1562, 276, 1594));
        tokens.add(t("버전", 0.999, 193, 1611, 250, 1643));
        tokens.add(t("설치", 0.895, 196, 1660, 253, 1695));
        tokens.add(t("날짜", 0.895, 262, 1660, 319, 1695));
        // Windows 사양 표: 값 열
        tokens.add(t("Windows", 0.996, 397, 1565, 518, 1591));
        tokens.add(t("11", 0.984, 524, 1565, 556, 1591));
        tokens.add(t("Enterprise", 0.984, 565, 1565, 694, 1594));
        tokens.add(t("25H2", 0.9998, 397, 1614, 469, 1640));
        return tokens;
    }

    @Test
    void extractsAllExpectedFieldsFromRealisticLayout() {
        List<OcrFieldExtraction> results =
                parser.parse(fullScreenshotTokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results).extracting(OcrFieldExtraction::fieldType)
                .containsExactlyInAnyOrder(
                        OcrFieldType.STORAGE_CAPACITY,
                        OcrFieldType.GPU,
                        OcrFieldType.RAM,
                        OcrFieldType.CPU,
                        OcrFieldType.OS_VERSION,
                        OcrFieldType.MODEL_NAME);

        assertThat(fieldValue(results, OcrFieldType.STORAGE_CAPACITY)).isEqualTo("954 GB");
        assertThat(fieldValue(results, OcrFieldType.GPU)).isEqualTo("6 GB");
        assertThat(fieldValue(results, OcrFieldType.RAM)).isEqualTo("32.0GB");
        assertThat(fieldValue(results, OcrFieldType.CPU)).isEqualTo("13th Gen Intel(R) Core(TM) i7-13700H");
        assertThat(fieldValue(results, OcrFieldType.OS_VERSION)).isEqualTo("Windows 11 Enterprise 25H2");
        assertThat(fieldValue(results, OcrFieldType.MODEL_NAME)).isEqualTo("960XFH");
    }

    @Test
    void modelNameConfidenceIsCappedBelowOtherFields() {
        List<OcrFieldExtraction> results =
                parser.parse(fullScreenshotTokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        BigDecimal modelNameConfidence = fieldConfidence(results, OcrFieldType.MODEL_NAME);
        BigDecimal cpuConfidence = fieldConfidence(results, OcrFieldType.CPU);

        assertThat(modelNameConfidence).isLessThan(cpuConfidence);
    }

    @Test
    void omitsModelNameWhenNoUnlabeledCodeFollowsTheHostname() {
        List<OcrToken> tokensWithoutOemCode = new ArrayList<>(fullScreenshotTokens());
        tokensWithoutOemCode.removeIf(token -> token.text().equals("960XFH"));

        List<OcrFieldExtraction> results =
                parser.parse(tokensWithoutOemCode, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results).extracting(OcrFieldExtraction::fieldType).doesNotContain(OcrFieldType.MODEL_NAME);
    }

    @Test
    void omitsCardRowFieldsWhenACardLabelIsMissing() {
        List<OcrToken> tokensWithoutGpuLabel = new ArrayList<>();
        for (OcrToken token : fullScreenshotTokens()) {
            if (!token.text().equals("그래픽") && !token.text().equals("카드")) {
                tokensWithoutGpuLabel.add(token);
            }
        }

        List<OcrFieldExtraction> results =
                parser.parse(tokensWithoutGpuLabel, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results)
                .extracting(OcrFieldExtraction::fieldType)
                .doesNotContain(
                        OcrFieldType.STORAGE_CAPACITY, OcrFieldType.GPU, OcrFieldType.RAM, OcrFieldType.CPU);
    }

    @Test
    void onlyReturnsFieldsThatAreExpected() {
        List<OcrFieldExtraction> results =
                parser.parse(fullScreenshotTokens(), Set.of(OcrFieldType.CPU, OcrFieldType.OS_VERSION));

        assertThat(results).extracting(OcrFieldExtraction::fieldType)
                .containsExactlyInAnyOrder(OcrFieldType.CPU, OcrFieldType.OS_VERSION);
    }

    @Test
    void returnsEmptyListWhenNoTokensMatchAnyLabel() {
        List<OcrToken> unrelated =
                List.of(
                        t("아무", 0.9, 0, 0, 10, 10),
                        t("관련", 0.9, 15, 0, 25, 10),
                        t("없는", 0.9, 30, 0, 40, 10),
                        t("화면", 0.9, 45, 0, 55, 10));

        List<OcrFieldExtraction> results = parser.parse(unrelated, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results).isEmpty();
    }

    private String fieldValue(List<OcrFieldExtraction> results, OcrFieldType fieldType) {
        return results.stream()
                .filter(r -> r.fieldType() == fieldType)
                .findFirst()
                .orElseThrow()
                .parsedValue();
    }

    private BigDecimal fieldConfidence(List<OcrFieldExtraction> results, OcrFieldType fieldType) {
        return results.stream()
                .filter(r -> r.fieldType() == fieldType)
                .findFirst()
                .orElseThrow()
                .confidence();
    }
}
