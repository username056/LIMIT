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
 * 실제 라이브 호출(Naver Clova, {@code system_info_screenshot.png})에서 확인한 좌표를 그대로 옮겨 파서를
 * 검증한다. 카드형 상단 영역(저장소/그래픽카드/설치된RAM/프로세서)과 표 영역(장치 사양, Windows 사양) 모두
 * 라이브 응답 좌표 기준이며, 표 영역은 라벨 몇 개가 있는지·순서가 어떤지 몰라도 "행 안의 큰 가로 간격"으로
 * 라벨과 값을 나누고 라벨 힌트·값 모양을 모두 만족할 때만 채택한다({@link SystemInfoScreenshotParser}의
 * {@code scanAllLabelValueRows}/{@code assignFieldsFromRows} 참고).
 */
class SystemInfoScreenshotParserTests {

    private final SystemInfoScreenshotParser parser = new SystemInfoScreenshotParser();

    private static OcrToken t(String text, double confidence, double left, double top, double right, double bottom) {
        return new OcrToken(text, new BigDecimal(String.valueOf(confidence)), left, top, right, bottom);
    }

    /**
     * 실제 라이브 테스트(NaverClovaOcrClient.recognizeFields, {@code system_info_screenshot.png} 원본)로
     * 확인한 좌표를 그대로 옮겼다. 카드 프로세서 값("13th Gen Intel(R) Core(TM) i7-13700H")이 카드 폭을
     * 넘겨 두 줄로 줄바꿈된 것, "장치 사양"/"Windows 사양" 표가 함께 펼쳐진 것을 모두 포함한다.
     */
    private static List<OcrToken> fullScreenshotTokens() {
        List<OcrToken> tokens = new ArrayList<>();
        // 헤더
        tokens.add(t("시스템", 0.998, 734, 112, 907, 172));
        tokens.add(t(">", 0.986, 944, 134, 967, 161));
        tokens.add(t("정보", 0.999, 993, 112, 1105, 172));
        // 카드 라벨 행
        tokens.add(t("저장소", 0.999, 813, 247, 888, 273));
        tokens.add(t("그래픽", 0.998, 1240, 247, 1312, 273));
        tokens.add(t("카드", 0.998, 1319, 247, 1368, 273));
        tokens.add(t("설치된", 0.999, 1660, 243, 1735, 273));
        tokens.add(t("RAM", 0.9997, 1743, 251, 1799, 273));
        tokens.add(t("프로세서", 0.999, 2088, 247, 2185, 273));
        // 카드 값 행 1 (저장소/그래픽카드/RAM 값 + 프로세서 값 1번째 줄)
        tokens.add(t("954", 0.558, 772, 299, 832, 333));
        tokens.add(t("GB", 0.558, 835, 299, 884, 333));
        tokens.add(t("6", 0.929, 1199, 307, 1222, 329));
        tokens.add(t("GB", 0.929, 1225, 307, 1270, 333));
        tokens.add(t("32.0GB", 0.993, 1619, 299, 1728, 333));
        tokens.add(t("13th", 0.9999, 2043, 299, 2110, 333));
        tokens.add(t("Gen", 0.9998, 2114, 303, 2177, 333));
        tokens.add(t("Intel(R)", 0.997, 2177, 299, 2290, 337));
        // 프로세서 값 2번째 줄 (카드 폭을 넘겨 줄바꿈됨)
        tokens.add(t("Core(TM)", 0.999, 2043, 344, 2185, 378));
        tokens.add(t("i7-13700H", 0.9996, 2185, 344, 2339, 378));
        // 호스트명 + 라벨 없는 OEM 코드 ("이 PC의 이름 바꾸기" 옆)
        tokens.add(t("DESKTOP-UB20P0O", 0.988, 775, 652, 1027, 678));
        tokens.add(t("960XFH", 0.988, 772, 686, 862, 708));
        // "장치 사양" 표: 라벨 열 (장치 이름 / 프로세서 / 설치된 RAM / 장치 ID / 제품 ID / 시스템 종류 / 펜 및 터치)
        tokens.add(t("장치", 0.998, 858, 910, 914, 944));
        tokens.add(t("이름", 0.998, 922, 910, 982, 944));
        tokens.add(t("프로세서", 0.999, 854, 959, 970, 993));
        tokens.add(t("설치된", 0.999, 854, 1008, 944, 1045));
        tokens.add(t("RAM", 0.9997, 948, 1012, 1015, 1042));
        tokens.add(t("장치", 0.828, 854, 1060, 914, 1094));
        tokens.add(t("ID", 0.828, 918, 1064, 955, 1094));
        tokens.add(t("제품", 0.955, 854, 1109, 918, 1143));
        tokens.add(t("ID", 0.955, 922, 1117, 955, 1143));
        tokens.add(t("시스템", 0.982, 858, 1162, 944, 1195));
        tokens.add(t("종류", 0.982, 952, 1162, 1012, 1195));
        tokens.add(t("펜", 0.997, 854, 1210, 888, 1244));
        tokens.add(t("및", 0.997, 895, 1210, 925, 1244));
        tokens.add(t("터치", 0.997, 933, 1210, 989, 1244));
        // "장치 사양" 표: 값 열
        tokens.add(t("DESKTOP-UB20P0O", 0.954, 1064, 918, 1319, 944));
        tokens.add(t("13th", 0.9999, 1064, 967, 1124, 993));
        tokens.add(t("Gen", 0.9997, 1132, 967, 1184, 993));
        tokens.add(t("Intel(R)", 0.993, 1184, 963, 1285, 997));
        tokens.add(t("Core(TM)", 0.999, 1285, 963, 1413, 997));
        tokens.add(t("i7-13700H(2.40", 0.997, 1413, 963, 1615, 997));
        tokens.add(t("GHz)", 0.997, 1615, 963, 1686, 997));
        tokens.add(t("32.0GB(31.6GB", 0.994, 1060, 1012, 1255, 1045));
        tokens.add(t("사용", 0.789, 1255, 1012, 1319, 1045));
        tokens.add(t("가능)", 0.789, 1323, 1012, 1394, 1045));
        tokens.add(t("9BBAD96A-2A40-4D64-9B49-71009D690A33", 0.998, 1060, 1064, 1638, 1094));
        tokens.add(t("00329-10330-57581-AA566", 0.996, 1064, 1117, 1420, 1143));
        tokens.add(t("64비트", 0.999, 1060, 1162, 1154, 1195));
        tokens.add(t("운영", 0.972, 1158, 1162, 1218, 1195));
        tokens.add(t("체제,", 0.972, 1225, 1162, 1293, 1195));
        tokens.add(t("x64", 0.835, 1293, 1162, 1345, 1192));
        tokens.add(t("기반", 0.835, 1349, 1162, 1409, 1195));
        tokens.add(t("프로세서", 0.999, 1417, 1162, 1529, 1195));
        tokens.add(t("이", 0.998, 1064, 1210, 1094, 1244));
        tokens.add(t("디스플레이에", 0.998, 1102, 1210, 1270, 1248));
        tokens.add(t("사용할", 0.999, 1278, 1210, 1368, 1248));
        tokens.add(t("수", 0.999, 1372, 1210, 1405, 1244));
        tokens.add(t("있는", 0.999, 1409, 1210, 1469, 1248));
        tokens.add(t("펜", 0.998, 1473, 1210, 1506, 1244));
        tokens.add(t("또는", 0.998, 1514, 1210, 1574, 1244));
        tokens.add(t("터치식", 0.998, 1581, 1210, 1668, 1244));
        tokens.add(t("입력이", 0.999, 1675, 1210, 1761, 1244));
        tokens.add(t("없습니다.", 0.983, 1769, 1210, 1893, 1244));
        // "Windows 사양" 표: 라벨 열 (에디션 / 버전 / 설치 날짜)
        tokens.add(t("에디션", 0.997, 858, 1559, 944, 1593));
        tokens.add(t("버전", 0.998, 858, 1608, 914, 1641));
        tokens.add(t("설치", 0.997, 858, 1660, 914, 1690));
        tokens.add(t("날짜", 0.997, 925, 1660, 982, 1690));
        // "Windows 사양" 표: 값 열
        tokens.add(t("Windows", 0.994, 1064, 1566, 1184, 1589));
        tokens.add(t("11", 0.9999, 1192, 1566, 1222, 1589));
        tokens.add(t("Enterprise", 0.9995, 1229, 1563, 1360, 1596));
        tokens.add(t("25H2", 0.9998, 1064, 1615, 1135, 1641));
        tokens.add(t("2024-04-01", 0.9998, 1064, 1668, 1214, 1690));
        return tokens;
    }

    /**
     * 실제 라이브 테스트(NaverClovaOcrClient.recognizeFields, {@code system_info_w10.png} 전처리)로 확인한
     * Windows 10 "설정 &gt; 시스템 &gt; 정보" 화면 좌표. Windows 11과 달리 카드형 요약이 없고 왼쪽에 "저장소"
     * 같은 사이드바 메뉴 항목이, 오른쪽에 "장치 이름/프로세서/설치된 RAM/..." 표만 있다. 왼쪽 사이드바의
     * "저장소" 메뉴 항목이 표의 "저장소" 카드 라벨과 같은 문구라 카드 인식 로직이 혼동할 뻔한 실제 사례다.
     */
    private static List<OcrToken> windows10Tokens() {
        List<OcrToken> tokens = new ArrayList<>();
        // 헤더 + 왼쪽 사이드바 메뉴(전부 서로 다른 세로 위치에 독립적으로 나열됨 — 카드가 아니다)
        tokens.add(t("정보", 0.999, 210, 71, 237, 87));
        tokens.add(t("장치", 0.996, 210, 95, 231, 106));
        tokens.add(t("사양", 0.996, 233, 95, 255, 106));
        tokens.add(t("시스템", 0.992, 46, 122, 69, 131));
        tokens.add(t("알림", 0.71, 62, 144, 78, 153));
        tokens.add(t("및", 0.71, 78, 144, 86, 153));
        tokens.add(t("작업", 0.71, 87, 144, 103, 153));
        tokens.add(t("집중", 0.99, 62, 168, 77, 178));
        tokens.add(t("지원", 0.99, 78, 168, 94, 177));
        tokens.add(t("전원", 0.92, 62, 192, 77, 201));
        tokens.add(t("및", 0.92, 78, 192, 86, 201));
        tokens.add(t("절전", 0.92, 87, 192, 103, 201));
        // 왼쪽 사이드바의 "저장소" 메뉴 항목 — 표의 카드 라벨과 같은 문구지만 카드가 아니다.
        tokens.add(t("저장소", 0.448, 62, 215, 84, 224));
        tokens.add(t("태블릿", 0.99, 62, 240, 84, 249));
        tokens.add(t("멀티태스킹", 0.91, 62, 263, 98, 273));
        // 오른쪽 "장치 사양" 표: 장치 이름 / 프로세서 / 설치된 RAM / 장치 ID / 제품 ID / 시스템 종류 / 펜 및 터치
        tokens.add(t("장치", 0.998, 210, 117, 226, 127));
        tokens.add(t("이름", 0.998, 226, 118, 242, 127));
        tokens.add(t("프로세서", 0.997, 210, 130, 240, 139));
        tokens.add(t("EZMS", 0.995, 261, 118, 281, 126));
        tokens.add(t("Intel(R)", 0.955, 261, 131, 286, 138));
        tokens.add(t("Core(TM)", 0.989, 285, 131, 318, 139));
        tokens.add(t("i9-9900K", 0.988, 317, 131, 348, 139));
        tokens.add(t("CPU", 0.809, 348, 131, 372, 139));
        tokens.add(t("3.60GHz", 0.992, 373, 131, 402, 139));
        tokens.add(t("3.60", 0.964, 407, 131, 422, 138));
        tokens.add(t("GHz", 0.918, 261, 140, 278, 148));
        tokens.add(t("설치된", 0.992, 210, 152, 233, 161));
        tokens.add(t("RAM", 0.993, 233, 153, 250, 161));
        tokens.add(t("8.00GB", 0.982, 260, 153, 285, 161));
        tokens.add(t("장치", 0.995, 209, 163, 226, 173));
        tokens.add(t("ID", 0.995, 225, 164, 235, 173));
        tokens.add(t("제품", 0.981, 209, 176, 226, 186));
        tokens.add(t("ID", 0.981, 225, 177, 235, 186));
        tokens.add(t("시스템", 0.981, 209, 189, 233, 199));
        tokens.add(t("종류", 0.981, 232, 189, 249, 199));
        tokens.add(t("펜", 0.962, 210, 201, 219, 211));
        tokens.add(t("및", 0.962, 218, 201, 228, 211));
        tokens.add(t("터치", 0.962, 228, 201, 244, 211));
        tokens.add(t("64비트", 0.999, 260, 190, 285, 198));
        tokens.add(t("운영", 0.984, 285, 190, 302, 199));
        tokens.add(t("체제,", 0.984, 302, 190, 321, 199));
        tokens.add(t("x64", 0.643, 319, 190, 333, 197));
        tokens.add(t("기반", 0.995, 334, 190, 350, 199));
        tokens.add(t("프로세서", 0.995, 350, 190, 380, 199));
        tokens.add(t("이", 0.962, 262, 202, 271, 211));
        tokens.add(t("디스플레이에", 0.962, 271, 202, 315, 211));
        tokens.add(t("사용할", 0.999, 315, 202, 339, 211));
        tokens.add(t("수", 0.998, 339, 203, 347, 211));
        tokens.add(t("있는", 0.998, 349, 202, 365, 211));
        tokens.add(t("펜", 0.99, 365, 202, 374, 211));
        tokens.add(t("또는", 0.99, 374, 202, 390, 211));
        tokens.add(t("터치식", 0.547, 391, 202, 414, 211));
        tokens.add(t("업력", 0.547, 414, 202, 430, 211));
        tokens.add(t("이", 0.992, 262, 211, 270, 220));
        tokens.add(t("없습니다.", 0.992, 271, 211, 303, 221));
        tokens.add(t("복사", 0.981, 226, 232, 241, 241));
        // Windows 사양 표: 에디션 / 버전
        tokens.add(t("에디션", 0.957, 210, 309, 232, 318));
        tokens.add(t("버전", 0.996, 210, 321, 226, 332));
        tokens.add(t("Windows", 0.993, 261, 310, 292, 317));
        tokens.add(t("10", 0.944, 292, 310, 303, 317));
        tokens.add(t("Pro", 0.944, 303, 310, 316, 317));
        tokens.add(t("21H2", 0.996, 261, 322, 280, 330));
        return tokens;
    }

    @Test
    void extractsCpuAndRamFromWindows10TableLayoutWithoutCards() {
        // Windows 10에는 카드형 요약이 없고 표만 있다 — 표 스캔만으로 CPU/RAM을 찾아야 한다.
        List<OcrFieldExtraction> results =
                parser.parse(windows10Tokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(fieldValue(results, OcrFieldType.CPU))
                .isEqualTo("Intel(R) Core(TM) i9-9900K CPU 3.60GHz 3.60");
        assertThat(fieldValue(results, OcrFieldType.RAM)).isEqualTo("8.00GB");
        assertThat(fieldValue(results, OcrFieldType.OS_VERSION))
                .isEqualTo("Windows 10 Pro 21H2 64비트 운영 체제, x64 기반 프로세서");
    }

    /**
     * msinfo32(시스템 정보) "시스템 요약" 화면의 라벨:값 표 일부. 설정 앱과 달리 에디션은 "OS 이름", 버전은
     * 커널 빌드 형식("10.0.26200 빌드 26200")으로 나온다.
     */
    private static List<OcrToken> msinfo32Tokens() {
        List<OcrToken> tokens = new ArrayList<>();
        tokens.add(t("OS", 0.99, 10, 10, 25, 20));
        tokens.add(t("이름", 0.99, 25, 10, 45, 20));
        tokens.add(t("Microsoft", 0.99, 100, 10, 160, 20));
        tokens.add(t("Windows", 0.99, 160, 10, 210, 20));
        tokens.add(t("11", 0.99, 210, 10, 220, 20));
        tokens.add(t("Enterprise", 0.99, 220, 10, 280, 20));
        tokens.add(t("버전", 0.99, 10, 30, 35, 40));
        tokens.add(t("10.0.26200", 0.99, 100, 30, 160, 40));
        tokens.add(t("빌드", 0.99, 160, 30, 180, 40));
        tokens.add(t("26200", 0.99, 180, 30, 210, 40));
        tokens.add(t("시스템", 0.99, 10, 50, 35, 60));
        tokens.add(t("종류", 0.99, 35, 50, 55, 60));
        tokens.add(t("x64", 0.99, 100, 50, 115, 60));
        tokens.add(t("기반", 0.99, 115, 50, 135, 60));
        tokens.add(t("PC", 0.99, 135, 50, 150, 60));
        return tokens;
    }

    @Test
    void composesOsVersionFromMsinfo32LayoutWithDifferentLabelsAndVersionFormat() {
        List<OcrFieldExtraction> results =
                parser.parse(msinfo32Tokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(fieldValue(results, OcrFieldType.OS_VERSION))
                .isEqualTo("Microsoft Windows 11 Enterprise 10.0.26200 빌드 26200 x64 기반 PC");
    }

    @Test
    void composesOsVersionSkippingMiddlePartWhenVersionRowIsNotRecognized() {
        // "버전" 행만 통째로 인식 안 된 경우를 흉내낸다 — 에디션과 시스템 종류 사이에 빈 자리나 이중 공백
        // 없이 바로 이어 붙어야 한다.
        List<OcrToken> tokens =
                windows10Tokens().stream()
                        .filter(token -> !"버전".equals(token.text()) && !"21H2".equals(token.text()))
                        .toList();

        List<OcrFieldExtraction> results = parser.parse(tokens, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(fieldValue(results, OcrFieldType.OS_VERSION))
                .isEqualTo("Windows 10 Pro 64비트 운영 체제, x64 기반 프로세서");
    }

    @Test
    void doesNotMistakeSidebarMenuItemsForCardValuesOnWindows10() {
        // 왼쪽 사이드바의 "저장소" 메뉴 항목이 카드 라벨과 같은 문구라도, 같은 가로줄에 있는 게 아니므로
        // 카드로 인식하지 않는다 — 그 아래 사이드바 메뉴 "태블릿"이 저장용량 값으로 잘못 채택되면 안 된다.
        List<OcrFieldExtraction> results =
                parser.parse(windows10Tokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results).extracting(OcrFieldExtraction::fieldType)
                .doesNotContain(OcrFieldType.STORAGE_CAPACITY, OcrFieldType.GPU, OcrFieldType.MODEL_NAME);
        assertThat(results).noneMatch(result -> "태블릿".equals(result.parsedValue()));
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
        assertThat(fieldValue(results, OcrFieldType.OS_VERSION))
                .isEqualTo("Windows 11 Enterprise 25H2 64비트 운영 체제, x64 기반 프로세서");
        assertThat(fieldValue(results, OcrFieldType.MODEL_NAME)).isEqualTo("DESKTOP-UB20P0O 960XFH");
    }

    @Test
    void prefersDeviceSpecTableValueOverCardValueForCpuAndRam() {
        // "장치 사양" 표가 더 상세하다(클럭 속도 포함, "사용 가능" 용량) — 카드 값 대신 표 값을 채택한다.
        List<OcrFieldExtraction> results =
                parser.parse(fullScreenshotTokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(fieldValue(results, OcrFieldType.CPU))
                .isEqualTo("13th Gen Intel(R) Core(TM) i7-13700H(2.40 GHz)");
        assertThat(fieldValue(results, OcrFieldType.RAM)).isEqualTo("32.0GB");
    }

    @Test
    void keepsCardValueWhenDeviceSpecTableIsNotExpanded() {
        // "장치 사양"을 펼치지 않은 화면처럼 그 표의 토큰이 아예 없으면 카드 값을 그대로 쓴다.
        List<OcrToken> withoutDeviceSpecTable = fullScreenshotTokens().stream()
                .filter(token -> token.left() < 800 || token.top() < 900 || token.top() > 1250)
                .toList();

        List<OcrFieldExtraction> results =
                parser.parse(withoutDeviceSpecTable, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(fieldValue(results, OcrFieldType.CPU)).isEqualTo("13th Gen Intel(R) Core(TM) i7-13700H");
        assertThat(fieldValue(results, OcrFieldType.RAM)).isEqualTo("32.0GB");
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
    void stillFindsOtherCardFieldsWhenOneCardLabelIsMissing() {
        // 카드 라벨 4개 중 하나(그래픽카드)가 없어도(다른 Windows 버전 등) 나머지는 찾은 대로 반영한다.
        List<OcrToken> tokensWithoutGpuLabel = new ArrayList<>();
        for (OcrToken token : fullScreenshotTokens()) {
            if (!token.text().equals("그래픽") && !token.text().equals("카드")) {
                tokensWithoutGpuLabel.add(token);
            }
        }

        List<OcrFieldExtraction> results =
                parser.parse(tokensWithoutGpuLabel, OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results).extracting(OcrFieldExtraction::fieldType).doesNotContain(OcrFieldType.GPU);
        assertThat(fieldValue(results, OcrFieldType.STORAGE_CAPACITY)).isEqualTo("954 GB");
        assertThat(fieldValue(results, OcrFieldType.RAM)).isEqualTo("32.0GB");
        assertThat(fieldValue(results, OcrFieldType.CPU))
                .isEqualTo("13th Gen Intel(R) Core(TM) i7-13700H(2.40 GHz)");
    }

    @Test
    void ignoresUnrelatedTableRowsLikeDeviceIdAndProductId() {
        // "장치 사양" 표의 장치 ID/제품 ID/펜 및 터치 같은, 아는 필드가 아닌 행은 결과에 안 섞인다.
        List<OcrFieldExtraction> results =
                parser.parse(fullScreenshotTokens(), OcrFieldExpectations.SCREENSHOT_FIELD_TYPES);

        assertThat(results)
                .noneMatch(result -> result.parsedValue().contains("9BBAD96A"))
                .noneMatch(result -> result.parsedValue().contains("00329-10330"));
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
