package com.c203.limit.domain.inspection.parser;

import com.c203.limit.domain.inspection.dto.OcrFieldExtraction;
import com.c203.limit.domain.inspection.dto.OcrToken;
import com.c203.limit.domain.inspection.enums.OcrFieldType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Windows "설정 &gt; 시스템 &gt; 정보" 화면 스크린샷의 OCR 토큰들을 필드별로 구조화한다.
 *
 * <p>네이버 클로바는 텍스트를 화면에 보이는 순서(라벨 바로 뒤에 값)로 반환하지 않고, 시각적으로 같은 영역(카드
 * 제목 줄 전체, 그다음 카드 값 줄 전체 / 표의 라벨 열 전체, 그다음 값 열 전체)을 묶어서 반환한다. 그래서 텍스트만
 * 이어붙여 정규식으로 찾는 방식은 실패하며, 각 조각의 바운딩 박스 좌표를 이용해 "라벨과 같은 세로 줄(행)에 있는
 * 값"을 찾는 방식으로 구조화한다. 실제 레이아웃이 가정과 다르면 해당 필드는 감지되지 않은 것으로 처리된다.
 */
@Component
public class SystemInfoScreenshotParser {

    private static final double ROW_TOLERANCE_RATIO = 0.6;
    private static final double ROW_STACK_GAP_RATIO = 2.0;
    private static final int MAX_VALUE_BLOCK_ROWS = 2;
    private static final int MAX_LABEL_LOOKAHEAD_TOKENS = 4;
    private static final int MAX_HANGUL_LABEL_SKIP = 6;

    private static final Pattern CAPACITY_PATTERN =
            Pattern.compile("(?i)[0-9]+(?:\\.[0-9]+)?\\s*(?:GB|TB|MB)");
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^[A-Z0-9-]{6,20}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4,10}$");

    private static final Set<OcrFieldType> CARD_ROW_FIELD_TYPES =
            Set.of(OcrFieldType.STORAGE_CAPACITY, OcrFieldType.GPU, OcrFieldType.RAM, OcrFieldType.CPU);

    /** MODEL_NAME은 라벨 없는 위치의 OEM 코드를 추정하는 것이라 신뢰도를 낮게 잡는다. */
    private static final BigDecimal MODEL_NAME_CONFIDENCE_CAP = new BigDecimal("0.500");

    public List<OcrFieldExtraction> parse(List<OcrToken> tokens, Set<OcrFieldType> expectedFieldTypes) {
        List<OcrFieldExtraction> results = new ArrayList<>();
        if (tokens.isEmpty()) {
            return results;
        }
        BigDecimal overallConfidence = averageConfidence(tokens);

        extractCardRow(tokens, expectedFieldTypes, overallConfidence, results);
        extractOsVersion(tokens, expectedFieldTypes, overallConfidence, results);
        extractModelName(tokens, expectedFieldTypes, overallConfidence, results);

        return results;
    }

    /**
     * 저장소 / 그래픽 카드 / 설치된 RAM / 프로세서 카드 4개가 나란히 배치된 상단 요약 영역.
     *
     * <p>클로바가 이 4개 라벨과 값을 반환하는 순서는 이미지마다 다르다 — 깨끗한 스크린샷에서는 "라벨 4개를 몰아서
     * 반환한 뒤 값 4개를 몰아서" 반환하지만, 카메라로 촬영한 사진 등에서는 "라벨 → 그 값 → 다음 라벨 → 그 값"처럼
     * 카드별로 붙여서 반환하는 경우가 있다(실제 라이브 테스트로 확인됨). 그래서 값은 "마지막 라벨 뒤"라는 한
     * 지점에서만 모으지 않고, 라벨 자신이 차지한 토큰 구간과 라벨 행 높이 안에 있는 토큰(라벨 행에 걸친 노이즈,
     * 예: 아이콘 오인식)을 제외한 나머지 후보 토큰 전체에서 모은다.
     */
    private void extractCardRow(
            List<OcrToken> tokens,
            Set<OcrFieldType> expected,
            BigDecimal confidence,
            List<OcrFieldExtraction> results) {
        if (Collections.disjoint(expected, CARD_ROW_FIELD_TYPES)) {
            return;
        }

        Optional<LabelMatch> storageLabel = findLabel(tokens, 0, "저장소");
        if (storageLabel.isEmpty()) {
            return;
        }
        Optional<LabelMatch> gpuLabel = findLabel(tokens, storageLabel.get().endIndex(), "그래픽카드");
        if (gpuLabel.isEmpty()) {
            return;
        }
        Optional<LabelMatch> ramLabel = findLabel(tokens, gpuLabel.get().endIndex(), "설치된RAM");
        if (ramLabel.isEmpty()) {
            return;
        }
        Optional<LabelMatch> cpuLabel = findLabel(tokens, ramLabel.get().endIndex(), "프로세서");
        if (cpuLabel.isEmpty()) {
            return;
        }

        double[] columnCenters = {
            storageLabel.get().centerX(), gpuLabel.get().centerX(), ramLabel.get().centerX(), cpuLabel.get().centerX()
        };
        List<OcrToken> valueCandidates =
                collectCardValueCandidates(
                        tokens, List.of(storageLabel.get(), gpuLabel.get(), ramLabel.get(), cpuLabel.get()));
        List<List<OcrToken>> valueRows = collectValueBlockRows(valueCandidates, 0);
        if (valueRows.isEmpty()) {
            return;
        }
        String[] values = splitRowsByNearestColumn(valueRows, columnCenters);

        addIfExpected(results, expected, OcrFieldType.STORAGE_CAPACITY, capacityOrRaw(values[0]), confidence);
        addIfExpected(results, expected, OcrFieldType.GPU, capacityOrRaw(values[1]), confidence);
        addIfExpected(results, expected, OcrFieldType.RAM, capacityOrRaw(values[2]), confidence);
        addIfExpected(results, expected, OcrFieldType.CPU, blankToEmpty(values[3]), confidence);
    }

    /**
     * 카드 4개의 라벨 자신이 차지한 토큰과, 라벨 행의 세로 범위 안에 있는 토큰(라벨 행 높이에 걸친 노이즈)을 뺀
     * 나머지를, 원래 순서를 유지한 채 값 후보로 모은다.
     */
    private List<OcrToken> collectCardValueCandidates(List<OcrToken> tokens, List<LabelMatch> labels) {
        int firstIndex = labels.stream().mapToInt(LabelMatch::startIndex).min().orElseThrow();
        double labelRowBottom = labels.stream().mapToDouble(LabelMatch::bottom).max().orElseThrow();

        List<OcrToken> candidates = new ArrayList<>();
        for (int i = firstIndex; i < tokens.size(); i++) {
            if (isWithinAnyLabel(i, labels)) {
                continue;
            }
            OcrToken token = tokens.get(i);
            if (token.top() > labelRowBottom) {
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

    /** Windows 사양 표의 "에디션"/"버전" 두 라벨의 값을 합쳐 하나의 OS_VERSION 값으로 만든다. */
    private void extractOsVersion(
            List<OcrToken> tokens,
            Set<OcrFieldType> expected,
            BigDecimal confidence,
            List<OcrFieldExtraction> results) {
        if (!expected.contains(OcrFieldType.OS_VERSION)) {
            return;
        }
        Optional<LabelMatch> editionLabel = findLabel(tokens, 0, "에디션");
        if (editionLabel.isEmpty()) {
            return;
        }
        Optional<LabelMatch> versionLabel = findLabel(tokens, editionLabel.get().endIndex(), "버전");
        if (versionLabel.isEmpty()) {
            return;
        }

        int valueStart = skipHangulOnlyTokens(tokens, versionLabel.get().endIndex(), MAX_HANGUL_LABEL_SKIP);
        Row editionValueRow = firstRowAfter(tokens, valueStart);
        if (editionValueRow.tokens().isEmpty()) {
            return;
        }
        Row versionValueRow = firstRowAfter(tokens, editionValueRow.nextIndex());
        if (versionValueRow.tokens().isEmpty()) {
            return;
        }

        String combined = (editionValueRow.text() + " " + versionValueRow.text()).trim();
        addIfExpected(
                results, expected, OcrFieldType.OS_VERSION, blankToEmpty(combined), confidence);
    }

    /**
     * "장치 이름"(호스트명) 아래에 라벨 없이 붙는 OEM 모델 코드(예: 960XFH)를 추정한다. 호스트명으로 보이는
     * 토큰 바로 다음에 짧은 영숫자 코드가 오는 첫 자리를 찾는다 — 조립 PC처럼 그런 코드가 없으면 감지되지 않는다.
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
                results.add(
                        new OcrFieldExtraction(
                                OcrFieldType.MODEL_NAME,
                                codeCandidate,
                                codeCandidate,
                                confidence.min(MODEL_NAME_CONFIDENCE_CAP)));
                return;
            }
        }
    }

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

    /** fromIndex의 토큰과 같은 세로 줄(행)에 속하는 연속 토큰들을 모은다. */
    private Row firstRowAfter(List<OcrToken> tokens, int fromIndex) {
        if (fromIndex >= tokens.size()) {
            return new Row(List.of(), fromIndex);
        }
        List<OcrToken> row = new ArrayList<>();
        OcrToken first = tokens.get(fromIndex);
        row.add(first);
        double refCenterY = first.centerY();
        double refHeight = Math.max(first.height(), 1.0);

        int i = fromIndex + 1;
        while (i < tokens.size() && Math.abs(tokens.get(i).centerY() - refCenterY) <= refHeight * ROW_TOLERANCE_RATIO) {
            row.add(tokens.get(i));
            i++;
        }
        return new Row(row, i);
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

    private int skipHangulOnlyTokens(List<OcrToken> tokens, int fromIndex, int maxSkip) {
        int i = fromIndex;
        int skipped = 0;
        while (i < tokens.size() && skipped < maxSkip && isHangulOnly(tokens.get(i).text())) {
            i++;
            skipped++;
        }
        return i;
    }

    private boolean isHangulOnly(String text) {
        return text != null
                && !text.isEmpty()
                && text.chars().allMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL);
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
    }

    private record Row(List<OcrToken> tokens, int nextIndex) {
        String text() {
            return tokens.stream()
                    .sorted(Comparator.comparingDouble(OcrToken::left))
                    .map(OcrToken::text)
                    .collect(Collectors.joining(" "));
        }
    }
}
