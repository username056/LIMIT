package com.c203.limit.domain.inspection.parser;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Windows "설정 &gt; 시스템 &gt; 정보" 화면 스크린샷의 OCR 토큰들을 필드별로 구조화한다.
 *
 * <p>표(장치 사양/Windows 사양) 영역은 라벨:값을 좌표만으로 찾는다 — 몇 번째 라벨까지 정확히 있어야 하는지
 * 정해두지 않고, 토큰을 세로 위치(행)로 먼저 묶은 뒤 각 행 안에서 큰 가로 간격을 기준으로 왼쪽(라벨 후보)과
 * 오른쪽(값 후보)을 나눈다. 그 라벨 후보가 아는 라벨과 같고 값 후보가 그 필드다운 모양(숫자+GB, Intel/AMD
 * 포함 등)일 때만 채택한다 — 이 이중 확인 덕분에 표에 없는 라벨 조합이거나 다른 Windows 버전이라 행 구성이
 * 달라져도 오탐 없이 "그 표에서 찾을 수 있는 것만" 찾아낸다. 상단 카드형 4열 요약 영역(저장소/그래픽카드/
 * 설치된RAM/프로세서)은 표와 레이아웃이 달라(값이 라벨과 같은 행이 아니라 다음 행에 열로 나뉘어 있음) 별도
 * 로직으로 처리하며, 라벨 4개 중 일부만 있어도 찾은 것만 반영한다.
 */
@Component
public class SystemInfoScreenshotParser {

    private static final double ROW_TOLERANCE_RATIO = 0.6;
    private static final double ROW_STACK_GAP_RATIO = 2.0;
    private static final int MAX_VALUE_BLOCK_ROWS = 2;
    private static final int MAX_LABEL_LOOKAHEAD_TOKENS = 4;

    /** 표 안에서 라벨과 값을 가르는 가로 간격이 그 행 평균 글자 높이의 이 배수보다 크면 "열이 나뉜다"고 본다. */
    private static final double TABLE_SPLIT_GAP_RATIO = 1.0;

    private static final Pattern CAPACITY_PATTERN =
            Pattern.compile("(?i)[0-9]+(?:\\.[0-9]+)?\\s*(?:GB|TB|MB)");
    private static final Pattern CPU_NAME_PATTERN =
            Pattern.compile("(?i).*(intel|amd|apple|ryzen|snapdragon|core\\s*i\\d).*");
    private static final Pattern GPU_NAME_PATTERN =
            Pattern.compile("(?i).*(intel|nvidia|amd|radeon|geforce|iris).*");
    private static final Pattern OS_VERSION_PATTERN = Pattern.compile("(?i).*(비트|x86|x64|프로세서).*");
    private static final Pattern OS_EDITION_PATTERN = Pattern.compile("(?i).*windows\\s*\\d+.*");
    // 설정 앱은 "25H2" 같은 짧은 기능 업데이트 버전을, msinfo32는 "10.0.26200 빌드 26200" 같은 커널 빌드
    // 버전을 "버전" 라벨에 담는다 — 둘 다 받아들이되 완전히 무관한 값은 걸러내도록 넉넉히 잡는다.
    private static final int OS_VERSION_TOKEN_MAX_LENGTH = 40;
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^[A-Z0-9-]{6,20}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4,10}$");

    private static final Set<OcrFieldType> CARD_ROW_FIELD_TYPES =
            Set.of(OcrFieldType.STORAGE_CAPACITY, OcrFieldType.GPU, OcrFieldType.RAM, OcrFieldType.CPU);

    /** 표 안에서 이 필드일 수 있는 라벨 후보(공백 제거된 형태). 언어를 늘릴 땐 여기에 후보만 추가하면 된다. */
    private static final Map<OcrFieldType, Set<String>> LABEL_HINTS = buildLabelHints();

    /** 라벨이 맞아도 값이 그 필드다운 모양이 아니면 버리기 위한 검증 규칙. */
    private static final Map<OcrFieldType, Predicate<String>> VALUE_VALIDATORS = buildValueValidators();

    /** MODEL_NAME은 라벨 없는 위치의 OEM 코드를 추정하는 것이라 신뢰도를 낮게 잡는다. */
    private static final BigDecimal MODEL_NAME_CONFIDENCE_CAP = new BigDecimal("0.500");

    public List<OcrFieldExtraction> parse(List<OcrToken> tokens, Set<OcrFieldType> expectedFieldTypes) {
        List<OcrFieldExtraction> results = new ArrayList<>();
        if (tokens.isEmpty()) {
            return results;
        }
        BigDecimal overallConfidence = averageConfidence(tokens);

        extractCardRow(tokens, expectedFieldTypes, overallConfidence, results);
        assignFieldsFromRows(scanAllLabelValueRows(tokens), expectedFieldTypes, overallConfidence, results);
        extractModelName(tokens, expectedFieldTypes, overallConfidence, results);

        return results;
    }

    // ===================== 카드형 상단 요약 (저장소 / 그래픽카드 / 설치된RAM / 프로세서) =====================

    /** 같은 카드 행으로 묶을 라벨들의 centerY가 이 배수(평균 라벨 높이 기준) 안에서 모여 있어야 한다. */
    private static final double CARD_LABEL_ALIGNMENT_RATIO = 1.5;

    /**
     * 저장소 / 그래픽 카드 / 설치된 RAM / 프로세서 카드가 나란히 배치된 상단 요약 영역. 4개 중 일부 라벨만
     * 찾아도(다른 Windows 버전이라 카드 구성이 다르거나, 라벨 하나가 오인식된 경우) 찾은 것만 반영한다.
     *
     * <p>4개가 항상 한 가로줄에 있는 건 아니다 — 창 너비가 좁아지면 3개+1개처럼 두 줄로 줄바꿈되는 반응형
     * 레이아웃도 있다(실제 확인된 사례: 그래픽카드까지 3개는 첫 줄, 프로세서만 다음 줄). 그래서 찾은 라벨
     * 전부가 한 줄에 있어야 한다고 요구하지 않고, {@link #groupLabelsIntoRows}로 먼저 세로 위치가 가까운
     * 라벨끼리 행 단위로 묶은 뒤, 각 행을 독립적으로 처리해 그 행에 있는 라벨들만으로 값을 찾는다.
     *
     * <p>클로바가 이 라벨들과 값을 반환하는 순서는 이미지마다 다르다 — 깨끗한 스크린샷에서는 "라벨들을 몰아서
     * 반환한 뒤 값들을 몰아서" 반환하지만, 카메라로 촬영한 사진 등에서는 "라벨 → 그 값 → 다음 라벨 → 그
     * 값"처럼 카드별로 붙여서 반환하는 경우가 있다(실제 라이브 테스트로 확인됨). 그래서 값은 "마지막 라벨 뒤"라는
     * 한 지점에서만 모으지 않고, 그 행의 라벨들이 차지한 토큰 구간과 라벨 행 높이 안에 있는 토큰(라벨 행에 걸친
     * 노이즈, 예: 아이콘 오인식)을 제외한 나머지 후보 토큰에서, 다음 행이 시작되기 전까지만 모은다.
     *
     * <p>Windows 10처럼 카드 없이 "저장소"/"설치된 RAM"/"프로세서"라는 같은 라벨 문구가 세로로 나열된 표만
     * 있는 화면에서는, 이 라벨들이 서로 전혀 다른 세로 위치에서 각자 독립된 행으로 묶인다 — 그런 표 레이아웃은
     * 값이 라벨 아래가 아니라 옆에 있어서 라벨 아래 후보 토큰이 그 필드다운 모양이 아니게 되고
     * {@link #VALUE_VALIDATORS}가 걸러낸다. 그렇게 걸러지지 않는 경우에 대비해 표 스캔
     * ({@link #assignFieldsFromRows})이 최종적으로 표 값을 우선 채택한다.
     */
    private void extractCardRow(
            List<OcrToken> tokens,
            Set<OcrFieldType> expected,
            BigDecimal confidence,
            List<OcrFieldExtraction> results) {
        if (Collections.disjoint(expected, CARD_ROW_FIELD_TYPES)) {
            return;
        }

        Map<OcrFieldType, LabelMatch> foundLabels = new EnumMap<>(OcrFieldType.class);
        findLabel(tokens, 0, "저장소").ifPresent(match -> foundLabels.put(OcrFieldType.STORAGE_CAPACITY, match));
        findLabel(tokens, 0, "그래픽카드").ifPresent(match -> foundLabels.put(OcrFieldType.GPU, match));
        findLabel(tokens, 0, "설치된RAM").ifPresent(match -> foundLabels.put(OcrFieldType.RAM, match));
        findLabel(tokens, 0, "프로세서").ifPresent(match -> foundLabels.put(OcrFieldType.CPU, match));
        if (foundLabels.isEmpty()) {
            return;
        }

        // rows는 centerY 오름차순이므로 rows.get(i + 1)은 항상 i행 바로 아래(다음 줄) 행이다. 클로바가
        // "라벨들을 몰아서 반환한 뒤 값들을 몰아서" 반환하는 스크린샷에서는 다음 행의 라벨 토큰이 배열 순서상
        // 이 행의 실제 값 토큰보다 먼저 나올 수 있다 — nextRowTop으로 그 다음 행 라벨의 최상단(top) 지점을
        // 명시적인 상한으로 넘겨야, collectCardValueCandidates가 그 라벨을 이 행의 값 후보로 잘못 주워
        // "첫 후보"로 오인해 진짜 값(더 뒤에 오는 후보)에 도달하지 못하는 오탐을 막을 수 있다.
        List<Map<OcrFieldType, LabelMatch>> rows = groupLabelsIntoRows(foundLabels);
        for (int i = 0; i < rows.size(); i++) {
            double nextRowTop =
                    i + 1 < rows.size()
                            ? rows.get(i + 1).values().stream().mapToDouble(LabelMatch::top).min().orElseThrow()
                            : Double.MAX_VALUE;
            extractCardValuesForRow(tokens, rows.get(i), nextRowTop, expected, confidence, results);
        }
    }

    /** 라벨들을 centerY로 정렬한 뒤, 인접한 라벨끼리 {@link #CARD_LABEL_ALIGNMENT_RATIO} 이내면 같은 행으로 묶는다. */
    private List<Map<OcrFieldType, LabelMatch>> groupLabelsIntoRows(Map<OcrFieldType, LabelMatch> foundLabels) {
        List<Map.Entry<OcrFieldType, LabelMatch>> sorted =
                foundLabels.entrySet().stream()
                        .sorted(Comparator.comparingDouble(entry -> entry.getValue().centerY()))
                        .toList();

        List<Map<OcrFieldType, LabelMatch>> rows = new ArrayList<>();
        Map<OcrFieldType, LabelMatch> current = new EnumMap<>(OcrFieldType.class);
        double refCenterY = 0;
        double refHeight = 1.0;
        for (Map.Entry<OcrFieldType, LabelMatch> entry : sorted) {
            LabelMatch label = entry.getValue();
            if (current.isEmpty()) {
                current.put(entry.getKey(), label);
                refCenterY = label.centerY();
                refHeight = Math.max(label.bottom() - label.top(), 1.0);
            } else if (Math.abs(label.centerY() - refCenterY) <= refHeight * CARD_LABEL_ALIGNMENT_RATIO) {
                current.put(entry.getKey(), label);
            } else {
                rows.add(current);
                current = new EnumMap<>(OcrFieldType.class);
                current.put(entry.getKey(), label);
                refCenterY = label.centerY();
                refHeight = Math.max(label.bottom() - label.top(), 1.0);
            }
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        return rows;
    }

    /** 한 카드 행에 있는 라벨들만으로 값을 찾아 결과에 반영한다. */
    private void extractCardValuesForRow(
            List<OcrToken> tokens,
            Map<OcrFieldType, LabelMatch> rowLabels,
            double nextRowTop,
            Set<OcrFieldType> expected,
            BigDecimal confidence,
            List<OcrFieldExtraction> results) {
        List<OcrFieldType> orderedFields = new ArrayList<>(rowLabels.keySet());
        double[] columnCenters =
                orderedFields.stream().mapToDouble(field -> rowLabels.get(field).centerX()).toArray();
        List<OcrToken> valueCandidates =
                collectCardValueCandidates(tokens, new ArrayList<>(rowLabels.values()), nextRowTop);
        List<List<OcrToken>> valueRows = collectValueBlockRows(valueCandidates, 0);
        if (valueRows.isEmpty()) {
            return;
        }
        String[] values = splitRowsByNearestColumn(valueRows, columnCenters);

        for (int i = 0; i < orderedFields.size(); i++) {
            OcrFieldType fieldType = orderedFields.get(i);
            Optional<String> value =
                    fieldType == OcrFieldType.CPU ? blankToEmpty(values[i]) : capacityOrRaw(values[i]);
            value.filter(text -> VALUE_VALIDATORS.get(fieldType).test(text))
                    .ifPresent(text -> addIfExpected(results, expected, fieldType, Optional.of(text), confidence));
        }
    }

    /**
     * 그 행의 라벨들이 차지한 토큰과 라벨 행 높이 안에 있는 토큰을 뺀 나머지를, 그 행의 라벨 아래(그리고 다음
     * 행이 시작되기 전)에서 원래 순서를 유지한 채 값 후보로 모은다.
     */
    private List<OcrToken> collectCardValueCandidates(
            List<OcrToken> tokens, List<LabelMatch> labels, double nextRowTop) {
        double labelRowBottom = labels.stream().mapToDouble(LabelMatch::bottom).max().orElseThrow();

        List<OcrToken> candidates = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (isWithinAnyLabel(i, labels)) {
                continue;
            }
            OcrToken token = tokens.get(i);
            if (token.top() > labelRowBottom && token.top() < nextRowTop) {
                candidates.add(token);
            }
        }
        return candidates;
    }

    private boolean isWithinAnyLabel(int index, List<LabelMatch> labels) {
        for (LabelMatch label : labels) {
            if (index >= label.startIndex() && index < label.endIndex()) {
                return true;
            }
        }
        return false;
    }

    /**
     * fromIndex부터 시작해 최대 {@value #MAX_VALUE_BLOCK_ROWS}개의 세로 줄(행)을 순서대로 모은다. 값이
     * 카드 폭을 넘겨 다음 줄로 줄바꿈된 경우(예: 긴 CPU 모델명)를 같은 값의 연속으로 포함하기 위함이며, 그
     * 다음에 오는 부가설명 줄(더 큰 간격)은 포함하지 않는다.
     */
    private List<List<OcrToken>> collectValueBlockRows(List<OcrToken> tokens, int fromIndex) {
        if (fromIndex >= tokens.size()) {
            return List.of();
        }
        List<List<OcrToken>> rows = new ArrayList<>();
        List<OcrToken> currentRow = new ArrayList<>();
        OcrToken first = tokens.get(fromIndex);
        currentRow.add(first);
        rows.add(currentRow);
        double refCenterY = first.centerY();
        double refHeight = Math.max(first.height(), 1.0);

        int i = fromIndex + 1;
        while (i < tokens.size()) {
            OcrToken token = tokens.get(i);
            double gap = Math.abs(token.centerY() - refCenterY);
            if (gap <= refHeight * ROW_TOLERANCE_RATIO) {
                currentRow.add(token);
                i++;
            } else if (gap <= refHeight * ROW_STACK_GAP_RATIO && rows.size() < MAX_VALUE_BLOCK_ROWS) {
                currentRow = new ArrayList<>();
                currentRow.add(token);
                rows.add(currentRow);
                refCenterY = token.centerY();
                refHeight = Math.max(token.height(), 1.0);
                i++;
            } else {
                break;
            }
        }
        return rows;
    }

    /** 행 순서(위→아래), 행 안에서는 x좌표 순서(왼쪽→오른쪽)를 지켜 각 열의 텍스트를 이어붙인다. */
    private String[] splitRowsByNearestColumn(List<List<OcrToken>> valueRows, double[] columnCenters) {
        StringBuilder[] columns = new StringBuilder[columnCenters.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = new StringBuilder();
        }
        for (List<OcrToken> row : valueRows) {
            List<OcrToken> sortedRow = row.stream().sorted(Comparator.comparingDouble(OcrToken::left)).toList();
            for (OcrToken token : sortedRow) {
                int nearest = nearestColumn(token, columnCenters);
                if (!columns[nearest].isEmpty()) {
                    columns[nearest].append(' ');
                }
                columns[nearest].append(token.text());
            }
        }
        String[] result = new String[columnCenters.length];
        for (int c = 0; c < columns.length; c++) {
            result[c] = columns[c].toString();
        }
        return result;
    }

    private int nearestColumn(OcrToken token, double[] columnCenters) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int c = 0; c < columnCenters.length; c++) {
            double distance = Math.abs(token.centerX() - columnCenters[c]);
            if (distance < best) {
                best = distance;
                nearest = c;
            }
        }
        return nearest;
    }

    // ===================== 라벨:값 표 (장치 사양 / Windows 사양) — 라벨 순서 무관 =====================

    /**
     * 화면 전체 토큰을 세로 위치(행)로 묶고, 각 행 안에서 뚜렷하게 큰 가로 간격들을 기준으로 여러 조각(라벨
     * 열/값 열 후보)으로 나눈 뒤, 인접한 두 조각씩을 라벨:값 후보 쌍으로 만든다. "장치 사양", "Windows 사양"
     * 처럼 라벨 열과 값 열이 나란한 표라면 이 간격이 라벨 안/값 안의 글자 간격보다 뚜렷하게 커서 정확히 그
     * 경계에서 갈린다(실제 좌표로 확인됨 — 예: "설치된 RAM" ↔ "32.0GB(31.6GB 사용 가능)" 사이 간격 45px vs
     * 같은 라벨 안 글자 간격 4~8px).
     *
     * <p>가장 큰 간격 하나로만 나누지 않고 기준을 넘는 간격마다 전부 나누는 이유: 사이드바 메뉴처럼 표와
     * 무관한 요소가 같은 세로 위치에 우연히 걸치면(예: Windows 10에서 "전원 및 절전" 사이드바 항목이 "시스템
     * 종류" 표 라벨과 같은 행에 묶이는 경우), 그 무관한 요소와의 간격이 표의 라벨:값 간격보다 더 클 수 있다.
     * 간격 하나로만 나누면 무관한 요소가 라벨로, 진짜 라벨은 값에 섞여버린다 — 인접 조각 쌍을 전부 후보로 만들면
     * "표 라벨:표 값" 쌍도 그중 하나로 포함되어 살아남는다(실제 라이브 데이터로 확인된 케이스).
     *
     * <p>표에 없는 조각 쌍(카드의 라벨만 있는 조각, 값만 있는 조각 등)도 후보로 만들어지지만, 그 라벨 후보가
     * {@link #LABEL_HINTS}에 없거나 값 후보가 {@link #VALUE_VALIDATORS}를 통과하지 못하면
     * {@link #assignFieldsFromRows}에서 버려지므로 오탐으로 이어지지 않는다.
     */
    private List<LabelValueRow> scanAllLabelValueRows(List<OcrToken> tokens) {
        List<LabelValueRow> rows = new ArrayList<>();
        for (List<OcrToken> row : groupIntoRowsByY(tokens)) {
            rows.addAll(toLabelValueRowCandidates(row));
        }
        return rows;
    }

    /** 토큰을 세로 위치(centerY) 기준으로 같은 행끼리 묶는다. 원본 토큰 배열의 순서는 신경 쓰지 않는다. */
    private List<List<OcrToken>> groupIntoRowsByY(List<OcrToken> tokens) {
        List<OcrToken> sortedByY =
                tokens.stream().sorted(Comparator.comparingDouble(OcrToken::centerY)).toList();
        List<List<OcrToken>> rows = new ArrayList<>();
        List<OcrToken> current = new ArrayList<>();
        double refCenterY = 0;
        double refHeight = 1.0;
        for (OcrToken token : sortedByY) {
            if (current.isEmpty()) {
                current.add(token);
                refCenterY = token.centerY();
                refHeight = Math.max(token.height(), 1.0);
            } else if (Math.abs(token.centerY() - refCenterY) <= refHeight * ROW_TOLERANCE_RATIO) {
                current.add(token);
            } else {
                rows.add(current);
                current = new ArrayList<>();
                current.add(token);
                refCenterY = token.centerY();
                refHeight = Math.max(token.height(), 1.0);
            }
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        return rows;
    }

    private List<LabelValueRow> toLabelValueRowCandidates(List<OcrToken> row) {
        if (row.size() < 2) {
            return List.of();
        }
        List<OcrToken> sorted = row.stream().sorted(Comparator.comparingDouble(OcrToken::left)).toList();
        double averageHeight =
                Math.max(sorted.stream().mapToDouble(OcrToken::height).average().orElse(1.0), 1.0);
        double minGap = averageHeight * TABLE_SPLIT_GAP_RATIO;

        List<List<OcrToken>> segments = new ArrayList<>();
        List<OcrToken> currentSegment = new ArrayList<>();
        currentSegment.add(sorted.get(0));
        for (int i = 0; i < sorted.size() - 1; i++) {
            double gap = sorted.get(i + 1).left() - sorted.get(i).right();
            if (gap >= minGap) {
                segments.add(currentSegment);
                currentSegment = new ArrayList<>();
            }
            currentSegment.add(sorted.get(i + 1));
        }
        segments.add(currentSegment);
        if (segments.size() < 2) {
            return List.of();
        }

        List<LabelValueRow> candidates = new ArrayList<>();
        for (int i = 0; i < segments.size() - 1; i++) {
            candidates.add(new LabelValueRow(joinText(segments.get(i)), joinText(segments.get(i + 1))));
        }
        return candidates;
    }

    private String joinText(List<OcrToken> tokens) {
        return tokens.stream().map(OcrToken::text).collect(Collectors.joining(" ")).trim();
    }

    /**
     * 찾아낸 라벨:값 행들 중 라벨이 아는 필드 후보와 같고 값이 그 필드다운 모양일 때만 채택한다.
     * OS_VERSION은 "Windows 사양" 표의 에디션·버전과 "장치 사양" 표의 "시스템 종류" 행 값을 순서대로 이어 붙인
     * 하나의 값(예: "Windows 11 Enterprise 25H2 64비트 운영 체제, x64 기반 프로세서")으로 만든다. 세 조각 중
     * 일부만 인식돼도(다른 Windows 버전이라 표 구성이 다르거나 한 조각이 오인식된 경우) 인식된 조각만 순서대로
     * 이어 붙이고, 하나도 인식되지 않으면 OS_VERSION 자체를 만들지 않는다.
     */
    private void assignFieldsFromRows(
            List<LabelValueRow> rows,
            Set<OcrFieldType> expected,
            BigDecimal confidence,
            List<OcrFieldExtraction> results) {
        for (Map.Entry<OcrFieldType, Set<String>> entry : LABEL_HINTS.entrySet()) {
            OcrFieldType fieldType = entry.getKey();
            if (!expected.contains(fieldType)) {
                continue;
            }
            findRowValue(rows, entry.getValue())
                    .map(value -> normalizeForField(fieldType, value))
                    .filter(value -> VALUE_VALIDATORS.get(fieldType).test(value))
                    .ifPresent(value -> upsertField(results, fieldType, value, confidence));
        }

        if (expected.contains(OcrFieldType.OS_VERSION)) {
            composeOsVersion(rows)
                    .ifPresent(value -> upsertField(results, OcrFieldType.OS_VERSION, value, confidence));
        }
    }

    /**
     * "Windows 사양" 표의 에디션("Windows 11 Enterprise")·버전("25H2")과 "장치 사양" 표의 시스템 종류
     * ("64비트 운영 체제, x64 기반 프로세서")를 이 순서대로 찾아, 인식된 조각만 공백으로 이어 붙인다. 중간에
     * 인식 안 된 조각이 있어도 건너뛸 뿐 빈 자리나 이중 공백을 남기지 않는다.
     *
     * <p>msinfo32(시스템 정보) 화면은 같은 정보를 다른 라벨로 보여준다 — 에디션은 "OS 이름"(예: "Microsoft
     * Windows 11 Enterprise"), 시스템 종류는 "시스템 종류"(예: "x64 기반 PC")로 나온다. "버전" 라벨은 같지만
     * 값이 "10.0.26200 빌드 26200"처럼 커널 빌드 형식이라 길이 제한만 다르게 잡는다. 두 화면 중 어느 쪽에서
     * 캡처했는지는 몰라도 되고, 그 화면에 있는 라벨만 찾아서 채택한다.
     */
    private Optional<String> composeOsVersion(List<LabelValueRow> rows) {
        List<String> parts = new ArrayList<>();
        findRowValue(rows, Set.of("에디션", "OS이름"))
                .map(String::trim)
                .filter(value -> !value.isBlank() && OS_EDITION_PATTERN.matcher(value).matches())
                .ifPresent(parts::add);
        findRowValue(rows, Set.of("버전"))
                .map(String::trim)
                .filter(value -> !value.isBlank() && value.length() <= OS_VERSION_TOKEN_MAX_LENGTH)
                .ifPresent(parts::add);
        findRowValue(rows, Set.of("시스템종류"))
                .map(String::trim)
                .filter(value -> !value.isBlank() && VALUE_VALIDATORS.get(OcrFieldType.OS_VERSION).test(value))
                .ifPresent(parts::add);
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", parts));
    }

    private Optional<String> findRowValue(List<LabelValueRow> rows, Set<String> labelHints) {
        return rows.stream()
                .filter(row -> labelHints.contains(compact(row.label())))
                .map(LabelValueRow::value)
                .findFirst();
    }

    private String compact(String text) {
        return text == null ? "" : text.replace(" ", "");
    }

    private String normalizeForField(OcrFieldType fieldType, String value) {
        if (fieldType == OcrFieldType.STORAGE_CAPACITY || fieldType == OcrFieldType.RAM) {
            return capacityOrRaw(value).orElse(value);
        }
        return value;
    }

    /** 이미 있던 같은 필드 결과를 지우고 새 값으로 교체한다(카드 값보다 표 값을 우선한다). */
    private void upsertField(
            List<OcrFieldExtraction> results,
            OcrFieldType fieldType,
            String value,
            BigDecimal confidence) {
        if (value == null || value.isBlank()) {
            return;
        }
        results.removeIf(result -> result.fieldType() == fieldType);
        results.add(new OcrFieldExtraction(fieldType, value.trim(), value.trim(), confidence));
    }

    private static Map<OcrFieldType, Set<String>> buildLabelHints() {
        Map<OcrFieldType, Set<String>> hints = new EnumMap<>(OcrFieldType.class);
        hints.put(OcrFieldType.STORAGE_CAPACITY, Set.of("저장소"));
        hints.put(OcrFieldType.GPU, Set.of("그래픽카드", "그래픽"));
        hints.put(OcrFieldType.RAM, Set.of("설치된RAM"));
        hints.put(OcrFieldType.CPU, Set.of("프로세서"));
        return Collections.unmodifiableMap(hints);
    }

    private static Map<OcrFieldType, Predicate<String>> buildValueValidators() {
        Map<OcrFieldType, Predicate<String>> validators = new EnumMap<>(OcrFieldType.class);
        Predicate<String> capacityLike = text -> CAPACITY_PATTERN.matcher(text).find();
        validators.put(OcrFieldType.STORAGE_CAPACITY, capacityLike);
        validators.put(OcrFieldType.RAM, capacityLike);
        validators.put(
                OcrFieldType.GPU, capacityLike.or(text -> GPU_NAME_PATTERN.matcher(text).find()));
        validators.put(OcrFieldType.CPU, text -> CPU_NAME_PATTERN.matcher(text).find());
        validators.put(OcrFieldType.OS_VERSION, text -> OS_VERSION_PATTERN.matcher(text).find());
        return Collections.unmodifiableMap(validators);
    }

    // ===================== 모델명 (라벨 없는 호스트명+코드 추정) =====================

    /**
     * "장치 이름"(호스트명) 아래에 라벨 없이 붙는 OEM 모델 코드(예: 960XFH)를 추정한다. 호스트명으로 보이는
     * 토큰 바로 다음에 짧은 영숫자 코드가 오는 첫 자리를 찾는다 — 조립 PC처럼 그런 코드가 없으면 감지되지 않는다.
     * 장치 이름과 모델 코드를 모두 보여주기 위해 "호스트명 코드" 형태로 합쳐서 하나의 MODEL_NAME 값으로 만든다.
     */
    private void extractModelName(
            List<OcrToken> tokens,
            Set<OcrFieldType> expected,
            BigDecimal confidence,
            List<OcrFieldExtraction> results) {
        if (!expected.contains(OcrFieldType.MODEL_NAME)) {
            return;
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String hostnameCandidate = tokens.get(i).text();
            String codeCandidate = tokens.get(i + 1).text();
            if (hostnameCandidate != null
                    && codeCandidate != null
                    && HOSTNAME_PATTERN.matcher(hostnameCandidate).matches()
                    && CODE_PATTERN.matcher(codeCandidate).matches()) {
                String combined = hostnameCandidate + " " + codeCandidate;
                results.add(
                        new OcrFieldExtraction(
                                OcrFieldType.MODEL_NAME,
                                combined,
                                combined,
                                confidence.min(MODEL_NAME_CONFIDENCE_CAP)));
                return;
            }
        }
    }

    // ===================== 공용 유틸 =====================

    private void addIfExpected(
            List<OcrFieldExtraction> results,
            Set<OcrFieldType> expected,
            OcrFieldType fieldType,
            Optional<String> value,
            BigDecimal confidence) {
        if (!expected.contains(fieldType) || value.isEmpty()) {
            return;
        }
        String text = value.get();
        results.add(new OcrFieldExtraction(fieldType, text, text, confidence));
    }

    private Optional<String> capacityOrRaw(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CAPACITY_PATTERN.matcher(text);
        return Optional.of(matcher.find() ? matcher.group().trim() : text.trim());
    }

    private Optional<String> blankToEmpty(String text) {
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
    }

    /** label(공백 무시) 문자열을 이루는 연속된 토큰 구간을 fromIndex부터 찾는다. */
    private Optional<LabelMatch> findLabel(List<OcrToken> tokens, int fromIndex, String label) {
        String compact = label.replace(" ", "");
        for (int i = fromIndex; i < tokens.size(); i++) {
            StringBuilder accumulated = new StringBuilder();
            double left = Double.MAX_VALUE;
            double top = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            double bottom = -Double.MAX_VALUE;
            int end = Math.min(tokens.size(), i + MAX_LABEL_LOOKAHEAD_TOKENS);
            for (int j = i; j < end; j++) {
                OcrToken token = tokens.get(j);
                accumulated.append(token.text());
                left = Math.min(left, token.left());
                top = Math.min(top, token.top());
                right = Math.max(right, token.right());
                bottom = Math.max(bottom, token.bottom());
                if (accumulated.toString().equals(compact)) {
                    return Optional.of(new LabelMatch(i, j + 1, left, top, right, bottom));
                }
            }
        }
        return Optional.empty();
    }

    private BigDecimal averageConfidence(List<OcrToken> tokens) {
        BigDecimal sum =
                tokens.stream().map(OcrToken::confidence).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(tokens.size()), 3, RoundingMode.HALF_UP);
    }

    private record LabelMatch(int startIndex, int endIndex, double left, double top, double right, double bottom) {
        double centerX() {
            return (left + right) / 2.0;
        }

        double centerY() {
            return (top + bottom) / 2.0;
        }
    }

    private record LabelValueRow(String label, String value) {}
}
