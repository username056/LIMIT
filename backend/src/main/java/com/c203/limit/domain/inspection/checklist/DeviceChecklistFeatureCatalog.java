package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DeviceChecklistFeatureCatalog {
    public static final int MAX_ADDITIONAL_ITEMS = 5;

    private final Map<DeviceType, Map<String, FeatureDefinition>> definitions;

    public DeviceChecklistFeatureCatalog() {
        this.definitions = definitions();
    }

    public List<FeatureDefinition> definitionsFor(DeviceType deviceType) {
        return List.copyOf(definitions
                .getOrDefault(deviceType, Map.of())
                .values());
    }

    public boolean supports(DeviceType deviceType, String featureCode) {
        return find(deviceType, featureCode).isPresent();
    }

    public Optional<FeatureDefinition> find(DeviceType deviceType, String featureCode) {
        if (deviceType == null || featureCode == null || featureCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions
                .getOrDefault(deviceType, Map.of())
                .get(normalize(featureCode)));
    }

    public List<GeneratedChecklistItem> additionalItems(
            DeviceType deviceType, Set<String> confirmedFeatures, int firstDisplayOrder) {
        if (confirmedFeatures == null || confirmedFeatures.isEmpty()) {
            return List.of();
        }
        List<GeneratedChecklistItem> items = new ArrayList<>();
        confirmedFeatures.stream()
                .map(DeviceChecklistFeatureCatalog::normalize)
                .distinct()
                .sorted()
                .map(code -> find(deviceType, code).orElseThrow())
                .filter(FeatureDefinition::canGenerateItem)
                .limit(MAX_ADDITIONAL_ITEMS)
                .forEach(definition ->
                        items.add(definition.toItem(firstDisplayOrder + items.size())));
        return List.copyOf(items);
    }

    private Map<DeviceType, Map<String, FeatureDefinition>> definitions() {
        Map<DeviceType, Map<String, FeatureDefinition>> result =
                new EnumMap<>(DeviceType.class);

        Map<String, FeatureDefinition> laptop = new LinkedHashMap<>();
        Arrays.stream(LaptopFeatureCode.values())
                .map(FeatureDefinition::laptop)
                .forEach(definition -> laptop.put(definition.code(), definition));
        result.put(DeviceType.LAPTOP, Map.copyOf(laptop));

        result.put(
                DeviceType.SMARTPHONE,
                index(List.of(
                        feature(
                                "WIRELESS_CHARGING",
                                "무선 충전",
                                "무선 충전 코일과 충전 인식 상태를 확인합니다.",
                                "호환 무선 충전기에 기기를 올리고 충전 표시와 배터리 증가를 촬영하세요.",
                                "PHN-FTR-WCHG",
                                EvidenceType.VIDEO),
                        feature(
                                "ESIM",
                                "eSIM",
                                "eSIM 메뉴와 개통 지원 여부를 확인합니다.",
                                "개인정보가 보이지 않도록 가린 뒤 eSIM 추가 메뉴가 활성화되는지 확인하세요.",
                                "PHN-FTR-ESIM",
                                EvidenceType.SELLER_CONFIRMATION),
                        feature(
                                "TELEPHOTO_CAMERA",
                                "망원 카메라",
                                "광학 망원 카메라의 초점과 촬영 상태를 확인합니다.",
                                "카메라 앱에서 망원 배율로 전환하고 먼 피사체의 초점이 맞는 과정을 촬영하세요.",
                                "PHN-FTR-TELE",
                                EvidenceType.VIDEO),
                        feature(
                                "NFC",
                                "NFC",
                                "NFC 기능 활성화와 태그 인식 상태를 확인합니다.",
                                "결제 정보가 노출되지 않도록 NFC를 켜고 안전한 NFC 태그 인식 여부를 확인하세요.",
                                "PHN-FTR-NFC",
                                EvidenceType.SELLER_CONFIRMATION),
                        feature(
                                "FINGERPRINT",
                                "지문 인식",
                                "지문 센서의 잠금 해제 동작을 확인합니다.",
                                "개인정보가 노출되지 않도록 잠금 화면에서 지문 인식 성공 여부만 촬영하세요.",
                                "PHN-FTR-FP",
                                EvidenceType.VIDEO),
                        feature(
                                "FACE_RECOGNITION",
                                "얼굴 인식",
                                "얼굴 인식 센서의 잠금 해제 동작을 확인합니다.",
                                "얼굴 정보가 자세히 노출되지 않도록 잠금 해제 성공 여부만 촬영하세요.",
                                "PHN-FTR-FACE",
                                EvidenceType.VIDEO))));

        result.put(
                DeviceType.FOLDABLE,
                index(List.of(
                        feature(
                                "COVER_DISPLAY",
                                "커버 디스플레이",
                                "접힌 상태의 외부 화면 표시와 터치 동작을 확인합니다.",
                                "기기를 접은 상태에서 커버 화면을 켜고 여러 영역을 터치하는 모습을 촬영하세요.",
                                "FLD-FTR-COVER",
                                EvidenceType.VIDEO),
                        feature(
                                "FLEX_MODE",
                                "플렉스 모드",
                                "반쯤 접은 상태의 힌지 고정과 화면 전환을 확인합니다.",
                                "지원 앱을 실행한 뒤 기기를 여러 각도로 접어 화면 배치와 힌지 고정을 촬영하세요.",
                                "FLD-FTR-FLEX",
                                EvidenceType.VIDEO),
                        feature(
                                "WIRELESS_CHARGING",
                                "무선 충전",
                                "무선 충전 코일과 충전 인식 상태를 확인합니다.",
                                "호환 무선 충전기에 기기를 올리고 충전 표시와 배터리 증가를 촬영하세요.",
                                "FLD-FTR-WCHG",
                                EvidenceType.VIDEO),
                        feature(
                                "ESIM",
                                "eSIM",
                                "eSIM 메뉴와 개통 지원 여부를 확인합니다.",
                                "개인정보가 보이지 않도록 가린 뒤 eSIM 추가 메뉴가 활성화되는지 확인하세요.",
                                "FLD-FTR-ESIM",
                                EvidenceType.SELLER_CONFIRMATION),
                        feature(
                                "STYLUS",
                                "스타일러스 펜",
                                "펜 입력과 화면 가장자리 인식 상태를 확인합니다.",
                                "메모 앱에서 선과 글자를 입력해 끊김과 입력 오차가 없는지 촬영하세요.",
                                "FLD-FTR-PEN",
                                EvidenceType.VIDEO),
                        feature(
                                "FINGERPRINT",
                                "지문 인식",
                                "지문 센서의 잠금 해제 동작을 확인합니다.",
                                "개인정보가 노출되지 않도록 잠금 화면에서 지문 인식 성공 여부만 촬영하세요.",
                                "FLD-FTR-FP",
                                EvidenceType.VIDEO))));

        result.put(
                DeviceType.TABLET,
                index(List.of(
                        feature(
                                "STYLUS",
                                "스타일러스 펜",
                                "펜 입력과 화면 가장자리 인식 상태를 확인합니다.",
                                "메모 앱에서 선과 글자를 입력해 끊김과 입력 오차가 없는지 촬영하세요.",
                                "TAB-FTR-PEN",
                                EvidenceType.VIDEO),
                        feature(
                                "KEYBOARD_CONNECTOR",
                                "키보드 커넥터",
                                "전용 키보드 연결과 키 입력 상태를 확인합니다.",
                                "호환 키보드를 연결하고 여러 키와 트랙패드가 동작하는 모습을 촬영하세요.",
                                "TAB-FTR-KBD",
                                EvidenceType.VIDEO),
                        feature(
                                "CELLULAR",
                                "LTE·5G 셀룰러",
                                "모바일 네트워크 지원과 연결 메뉴를 확인합니다.",
                                "전화번호 등 개인정보를 가린 뒤 모바일 네트워크 메뉴와 연결 상태를 확인하세요.",
                                "TAB-FTR-LTE",
                                EvidenceType.SELLER_CONFIRMATION),
                        feature(
                                "FACE_RECOGNITION",
                                "얼굴 인식",
                                "얼굴 인식 센서의 잠금 해제 동작을 확인합니다.",
                                "얼굴 정보가 자세히 노출되지 않도록 잠금 해제 성공 여부만 촬영하세요.",
                                "TAB-FTR-FACE",
                                EvidenceType.VIDEO),
                        feature(
                                "FINGERPRINT",
                                "지문 인식",
                                "지문 센서의 잠금 해제 동작을 확인합니다.",
                                "개인정보가 노출되지 않도록 잠금 화면에서 지문 인식 성공 여부만 촬영하세요.",
                                "TAB-FTR-FP",
                                EvidenceType.VIDEO),
                        feature(
                                "OLED",
                                "OLED 디스플레이",
                                "OLED 화면의 번인과 색상 이상 여부를 확인합니다.",
                                "흰색과 회색 단색 화면을 전체 화면으로 띄워 번인과 변색 여부를 촬영하세요.",
                                "TAB-FTR-OLED",
                                EvidenceType.VIDEO),
                        feature(
                                "DESKTOP_MODE",
                                "데스크톱 모드",
                                "외부 화면 또는 데스크톱 UI 전환 기능을 확인합니다.",
                                "데스크톱 모드를 실행해 창 전환과 입력 장치 연결이 동작하는 모습을 촬영하세요.",
                                "TAB-FTR-DESK",
                                EvidenceType.VIDEO))));

        return Map.copyOf(result);
    }

    private static Map<String, FeatureDefinition> index(List<FeatureDefinition> definitions) {
        Map<String, FeatureDefinition> indexed = new LinkedHashMap<>();
        definitions.forEach(definition -> indexed.put(definition.code(), definition));
        return Map.copyOf(indexed);
    }

    private static FeatureDefinition feature(
            String code,
            String displayName,
            String purpose,
            String guide,
            String itemCode,
            EvidenceType evidenceType) {
        return new FeatureDefinition(
                code, displayName, purpose, guide, itemCode, evidenceType);
    }

    private static String normalize(String featureCode) {
        return featureCode.trim().toUpperCase(Locale.ROOT);
    }

    public record FeatureDefinition(
            String code,
            String displayName,
            String purpose,
            String checkGuide,
            String itemCode,
            EvidenceType evidenceType) {

        private static FeatureDefinition laptop(LaptopFeatureCode featureCode) {
            return new FeatureDefinition(
                    featureCode.name(),
                    featureCode.displayNameKo(),
                    featureCode.displayNameKo() + " 기능 상태를 확인합니다.",
                    featureCode.defaultCheckGuideKo(),
                    null,
                    null);
        }

        boolean canGenerateItem() {
            return itemCode != null && evidenceType != null;
        }

        GeneratedChecklistItem toItem(int displayOrder) {
            if (!canGenerateItem()) {
                throw new IllegalStateException("feature does not define a generated item");
            }
            return new GeneratedChecklistItem(
                    itemCode,
                    displayName,
                    purpose,
                    checkGuide,
                    evidenceType,
                    AutomationType.NONE,
                    null,
                    true,
                    displayOrder,
                    code,
                    null,
                    null,
                    null);
        }
    }
}
