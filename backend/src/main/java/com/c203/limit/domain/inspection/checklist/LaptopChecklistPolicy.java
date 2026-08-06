package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.product.entity.OsFamily;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LaptopChecklistPolicy {
    public static final int MAX_ADDITIONAL_ITEMS = 5;

    private final Map<LaptopFeatureCode, ItemDefinition> featureItems = featureItems();

    public List<GeneratedChecklistItem> generate(
            OsFamily osFamily, Set<LaptopFeatureCode> confirmedFeatures) {
        List<GeneratedChecklistItem> result = new ArrayList<>(baseItems(osFamily));
        result.addAll(additionalItems(confirmedFeatures, result.size() + 1));
        for (int index = 0; index < result.size(); index++) {
            result.set(index, result.get(index).withDisplayOrder(index + 1));
        }
        return List.copyOf(result);
    }

    public List<GeneratedChecklistItem> additionalItems(
            Set<LaptopFeatureCode> confirmedFeatures, int firstDisplayOrder) {
        List<GeneratedChecklistItem> result = new ArrayList<>();
        Set<LaptopFeatureCode> uniqueFeatures = confirmedFeatures == null
                ? Set.of()
                : new LinkedHashSet<>(confirmedFeatures);
        uniqueFeatures.stream()
                .filter(featureItems::containsKey)
                .sorted()
                .limit(MAX_ADDITIONAL_ITEMS)
                .map(featureItems::get)
                .map(definition -> definition.toItem(firstDisplayOrder + result.size()))
                .forEach(result::add);
        return List.copyOf(result);
    }

    public EvidenceType evidenceType(LaptopFeatureCode featureCode) {
        ItemDefinition definition = featureItems.get(featureCode);
        if (definition == null) {
            throw new IllegalArgumentException("unsupported laptop feature");
        }
        return definition.evidenceType();
    }

    public String itemCode(LaptopFeatureCode featureCode) {
        ItemDefinition definition = featureItems.get(featureCode);
        if (definition == null) {
            throw new IllegalArgumentException("unsupported laptop feature");
        }
        return definition.code();
    }

    public boolean supports(LaptopFeatureCode featureCode) {
        return featureItems.containsKey(featureCode);
    }

    private List<GeneratedChecklistItem> baseItems(OsFamily osFamily) {
        List<ItemDefinition> definitions = new ArrayList<>(List.of(
                required(
                        "LAP-ID-001",
                        "기기 식별 정보",
                        "모델명과 시리얼 번호를 확인합니다.",
                        "제품 하판의 모델명과 시리얼 번호가 보이도록 촬영하세요.",
                        EvidenceType.PHOTO),
                required(
                        "LAP-EXT-002",
                        "외관 상태",
                        "찍힘, 균열, 휨과 사용 흔적을 확인합니다.",
                        "상판, 하판과 네 모서리를 밝은 곳에서 차례로 촬영하세요.",
                        EvidenceType.PHOTO),
                required(
                        "LAP-HNG-012",
                        "힌지 상태",
                        "화면을 지지하는 힌지의 유격, 소음, 파손과 고정력을 확인합니다.",
                        "덮개를 천천히 완전히 열고 닫아 좌우 힌지의 흔들림, 소음, 들뜸과 화면 고정 상태가 보이도록 촬영하세요.",
                        EvidenceType.VIDEO),
                required(
                        "LAP-DSP-003",
                        "디스플레이 상태",
                        "화면 파손, 멍, 줄, 불량 화소를 확인합니다.",
                        "흰색과 검은색 화면을 전체 화면으로 띄운 뒤 화면 전체를 촬영하세요.",
                        EvidenceType.VIDEO),
                required(
                        "LAP-PWR-004",
                        "전원 및 부팅",
                        "전원 버튼과 운영체제 부팅 상태를 확인합니다.",
                        "전원이 꺼진 상태부터 로그인 화면이 나타날 때까지 촬영하세요.",
                        EvidenceType.VIDEO),
                required(
                        "LAP-KBD-005",
                        "키보드",
                        "키 입력과 키캡 상태를 확인합니다.",
                        "웹 기반 점검에서 전체 키 입력이 정상 인식되는지 확인하세요.",
                        EvidenceType.SELLER_CONFIRMATION),
                required(
                        "LAP-PAD-006",
                        "터치패드",
                        "포인터 이동, 클릭, 스크롤 동작을 확인합니다.",
                        "웹 기반 점검에서 포인터 이동, 클릭, 스크롤이 정상 동작하는지 확인하세요.",
                        EvidenceType.SELLER_CONFIRMATION),
                required(
                        "LAP-SPEC-008",
                        "실제 사양 확인",
                        "판매자가 CPU, 메모리, 저장장치, GPU 정보를 확인합니다.",
                        "시스템 정보와 실제 장착 사양을 비교한 뒤 판매자가 확인하세요.",
                        EvidenceType.SELLER_CONFIRMATION),
                required(
                        "LAP-PRV-009",
                        "계정 로그아웃 및 초기화",
                        "개인정보와 기기 잠금이 남지 않았는지 확인합니다.",
                        "개인 계정 로그아웃, 기기 찾기 해제, 초기화를 완료한 뒤 확인하세요.",
                        EvidenceType.SELLER_CONFIRMATION)));

        if (osFamily == OsFamily.WINDOWS) {
            definitions.add(fileRequired(
                    "LAP-BAT-010",
                    "배터리 성능",
                    "배터리 설계 용량과 현재 완전 충전 용량을 확인합니다.",
                    "powercfg /batteryreport로 battery-report.html을 생성해 등록하세요. 생성이 불가능하면 배터리 상태 화면 캡처를 등록하세요.",
                    "BATTERY_REPORT"));
            definitions.add(fileRequired(
                    "LAP-SYS-011",
                    "Windows 시스템 진단",
                    "운영체제와 주요 하드웨어 사양을 확인합니다.",
                    "dxdiag에서 모든 정보 저장을 선택해 DxDiag.txt를 등록하세요. 저장이 불가능하면 시스템 정보 화면 캡처를 등록하세요.",
                    "DXDIAG"));
            definitions.add(ocrRequired(
                    "LAP-SCR-013",
                    "기기 정보 화면",
                    "설정 화면에 표시되는 모델명, CPU, RAM, GPU, 저장용량, OS 버전을 확인합니다.",
                    "설정 > 시스템 > 정보 화면을 캡처해 등록하세요."));
        } else {
            definitions.add(required(
                    "LAP-BAT-010",
                    "배터리 성능",
                    "Linux에서 배터리 건강도와 충전 상태를 확인합니다.",
                    "전원 설정 또는 upower 결과에서 배터리 상태가 보이도록 스크린샷을 등록하세요.",
                    EvidenceType.PHOTO));
            definitions.add(required(
                    "LAP-SYS-011",
                    "Linux 시스템 정보",
                    "배포판, 커널과 주요 하드웨어 사양을 확인합니다.",
                    "설정의 정보 화면 또는 시스템 정보 명령 결과를 스크린샷으로 등록하세요.",
                    EvidenceType.PHOTO));
        }

        List<GeneratedChecklistItem> items = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            items.add(definitions.get(index).toItem(index + 1));
        }
        return items;
    }

    private Map<LaptopFeatureCode, ItemDefinition> featureItems() {
        Map<LaptopFeatureCode, ItemDefinition> items =
                new EnumMap<>(LaptopFeatureCode.class);
        items.put(
                LaptopFeatureCode.PORTS,
                feature(
                        "LAP-FTR-PORT",
                        "외부 포트",
                        "USB, HDMI 등 외부 포트 동작을 확인합니다.",
                        "지원하는 포트마다 장치를 연결해 인식되는 화면과 연결 상태를 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.RJ45_PORT,
                feature(
                        "LAP-FTR-RJ45",
                        "유선 LAN(RJ45) 포트",
                        "유선 네트워크 연결과 포트의 물리적 고정 상태를 확인합니다.",
                        "랜선을 연결해 커넥터가 고정되는 모습과 유선 네트워크로 웹 페이지가 열리는 과정을 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.MICROSD_SLOT,
                feature(
                        "LAP-FTR-MSD",
                        "microSD 카드 슬롯",
                        "microSD 카드 삽입과 읽기 기능을 확인합니다.",
                        "microSD 카드를 삽입한 뒤 운영체제에서 카드와 파일 목록이 인식되는 과정을 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.CAMERA,
                featureSpecOnly(
                        "LAP-FTR-CAM",
                        "카메라",
                        "내장 카메라 탑재 여부를 확인합니다.",
                        "판매자가 이 기기에 카메라가 있음을 확인합니다.",
                        EvidenceType.SELLER_CONFIRMATION));
        items.put(
                LaptopFeatureCode.MICROPHONE,
                featureSpecOnly(
                        "LAP-FTR-MIC",
                        "마이크",
                        "내장 마이크 탑재 여부를 확인합니다.",
                        "판매자가 이 기기에 마이크가 있음을 확인합니다.",
                        EvidenceType.SELLER_CONFIRMATION));
        items.put(
                LaptopFeatureCode.SPEAKERS,
                featureSpecOnly(
                        "LAP-FTR-SPK",
                        "스피커",
                        "스피커 탑재 여부를 확인합니다.",
                        "판매자가 이 기기에 스피커가 있음을 확인합니다.",
                        EvidenceType.SELLER_CONFIRMATION));
        items.put(
                LaptopFeatureCode.WIFI,
                feature(
                        "LAP-FTR-WIFI",
                        "Wi-Fi",
                        "무선 네트워크 연결을 확인합니다.",
                        "Wi-Fi 연결 후 웹 페이지가 열리는 과정을 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.BLUETOOTH,
                feature(
                        "LAP-FTR-BT",
                        "Bluetooth",
                        "Bluetooth 검색과 연결을 확인합니다.",
                        "Bluetooth 장치 검색 및 연결 완료 화면을 촬영하세요.",
                        EvidenceType.PHOTO));
        items.put(
                LaptopFeatureCode.TOUCHSCREEN,
                featureSpecOnly(
                        "LAP-FTR-TOUCH",
                        "터치스크린",
                        "터치스크린 탑재 여부를 확인합니다.",
                        "판매자가 이 기기에 터치스크린이 있음을 확인합니다.",
                        EvidenceType.SELLER_CONFIRMATION));
        items.put(
                LaptopFeatureCode.CONVERTIBLE_HINGE,
                feature(
                        "LAP-FTR-360",
                        "360도 힌지",
                        "컨버터블 힌지와 모드 전환을 확인합니다.",
                        "노트북 모드에서 태블릿 모드까지 천천히 전환하는 모습을 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.STYLUS,
                featureSpecOnly(
                        "LAP-FTR-PEN",
                        "스타일러스",
                        "스타일러스 지원 여부를 확인합니다.",
                        "판매자가 이 기기에서 스타일러스를 지원함을 확인합니다.",
                        EvidenceType.SELLER_CONFIRMATION));
        items.put(
                LaptopFeatureCode.FINGERPRINT,
                feature(
                        "LAP-FTR-FP",
                        "지문 인식",
                        "지문 센서 동작을 확인합니다.",
                        "개인정보가 노출되지 않도록 로그인 성공 여부만 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.FACE_RECOGNITION,
                feature(
                        "LAP-FTR-FACE",
                        "얼굴 인식",
                        "IR 카메라 기반 얼굴 로그인을 확인합니다.",
                        "얼굴 정보가 노출되지 않도록 로그인 성공 여부만 촬영하세요.",
                        EvidenceType.VIDEO));
        items.put(
                LaptopFeatureCode.DEDICATED_GPU,
                feature(
                        "LAP-FTR-DGPU",
                        "외장 GPU",
                        "전용 그래픽 장치 인식과 정보를 확인합니다.",
                        "시스템 정보에서 GPU 모델과 전용 메모리가 보이도록 촬영하세요.",
                        EvidenceType.PHOTO));
        items.put(
                LaptopFeatureCode.CELLULAR,
                feature(
                        "LAP-FTR-LTE",
                        "셀룰러 통신",
                        "LTE 또는 5G 모뎀 인식과 연결을 확인합니다.",
                        "SIM 정보는 가리고 모바일 네트워크 연결 상태를 촬영하세요.",
                        EvidenceType.PHOTO));
        items.put(
                LaptopFeatureCode.OLED,
                feature(
                        "LAP-FTR-OLED",
                        "OLED 화면",
                        "OLED 번인과 색상 이상을 확인합니다.",
                        "회색 단색 화면을 전체 화면으로 띄워 잔상 여부를 촬영하세요.",
                        EvidenceType.PHOTO));
        items.put(
                LaptopFeatureCode.NUMPAD,
                feature(
                        "LAP-FTR-NUM",
                        "숫자 키패드",
                        "숫자 키패드 입력을 확인합니다.",
                        "웹 기반 점검에서 숫자 키패드 입력이 정상 인식되는지 확인하세요.",
                        EvidenceType.SELLER_CONFIRMATION));
        items.put(
                LaptopFeatureCode.THUNDERBOLT,
                feature(
                        "LAP-FTR-TB",
                        "Thunderbolt",
                        "Thunderbolt 포트의 장치 인식을 확인합니다.",
                        "호환 장치를 연결해 연결 관리자 또는 장치 인식 화면을 촬영하세요.",
                        EvidenceType.PHOTO));
        items.put(
                LaptopFeatureCode.SD_CARD,
                feature(
                        "LAP-FTR-SD",
                        "SD 카드 리더",
                        "메모리 카드 삽입과 읽기를 확인합니다.",
                        "카드 삽입 후 파일 목록이 열리는 과정을 촬영하세요.",
                        EvidenceType.VIDEO));
        return Map.copyOf(items);
    }

    private static ItemDefinition required(
            String code,
            String name,
            String purpose,
            String guide,
            EvidenceType evidenceType) {
        return new ItemDefinition(
                code,
                name,
                purpose,
                guide,
                evidenceType,
                AutomationType.NONE,
                null,
                null,
                true,
                true);
    }

    private static ItemDefinition fileRequired(
            String code, String name, String purpose, String guide, String parserType) {
        return new ItemDefinition(
                code,
                name,
                purpose,
                guide,
                EvidenceType.DIAGNOSTIC_FILE,
                AutomationType.FILE_PARSE,
                parserType,
                null,
                true,
                true);
    }

    private static ItemDefinition ocrRequired(String code, String name, String purpose, String guide) {
        return new ItemDefinition(
                code,
                name,
                purpose,
                guide,
                EvidenceType.PHOTO,
                AutomationType.OCR,
                null,
                null,
                true,
                true);
    }

    private static ItemDefinition feature(
            String code,
            String name,
            String purpose,
            String guide,
            EvidenceType evidenceType) {
        return new ItemDefinition(
                code,
                name,
                purpose,
                guide,
                evidenceType,
                AutomationType.NONE,
                null,
                null,
                true,
                true);
    }

    /**
     * 실동작 점검 없이 판매자 확인만으로 남는 선택 기능. 필수 검증 항목이 아니므로 등록 완료를 막지 않고,
     * 검증한 적 없는 항목이 구매자에게 미완료 체크리스트로 비치지 않도록 buyer 화면에서도 숨긴다.
     */
    private static ItemDefinition featureSpecOnly(
            String code,
            String name,
            String purpose,
            String guide,
            EvidenceType evidenceType) {
        return new ItemDefinition(
                code,
                name,
                purpose,
                guide,
                evidenceType,
                AutomationType.NONE,
                null,
                null,
                false,
                false);
    }

    private record ItemDefinition(
            String code,
            String name,
            String purpose,
            String guide,
            EvidenceType evidenceType,
            AutomationType automationType,
            String parserType,
            LaptopFeatureCode featureCode,
            boolean required,
            boolean visibleToBuyer) {

        GeneratedChecklistItem toItem(int order) {
            LaptopFeatureCode resolvedFeature = featureCode;
            if (resolvedFeature == null && code.startsWith("LAP-FTR-")) {
                resolvedFeature = featureFromCode(code);
            }
            return new GeneratedChecklistItem(
                    code,
                    name,
                    purpose,
                    guide,
                    evidenceType,
                    automationType,
                    parserType,
                    required,
                    visibleToBuyer,
                    order,
                                resolvedFeature == null ? null : resolvedFeature.name(),
                    null,
                    null,
                    null);
        }

        private static LaptopFeatureCode featureFromCode(String code) {
            return switch (code) {
                case "LAP-FTR-PORT" -> LaptopFeatureCode.PORTS;
                case "LAP-FTR-RJ45" -> LaptopFeatureCode.RJ45_PORT;
                case "LAP-FTR-MSD" -> LaptopFeatureCode.MICROSD_SLOT;
                case "LAP-FTR-CAM" -> LaptopFeatureCode.CAMERA;
                case "LAP-FTR-MIC" -> LaptopFeatureCode.MICROPHONE;
                case "LAP-FTR-SPK" -> LaptopFeatureCode.SPEAKERS;
                case "LAP-FTR-WIFI" -> LaptopFeatureCode.WIFI;
                case "LAP-FTR-BT" -> LaptopFeatureCode.BLUETOOTH;
                case "LAP-FTR-TOUCH" -> LaptopFeatureCode.TOUCHSCREEN;
                case "LAP-FTR-360" -> LaptopFeatureCode.CONVERTIBLE_HINGE;
                case "LAP-FTR-PEN" -> LaptopFeatureCode.STYLUS;
                case "LAP-FTR-FP" -> LaptopFeatureCode.FINGERPRINT;
                case "LAP-FTR-FACE" -> LaptopFeatureCode.FACE_RECOGNITION;
                case "LAP-FTR-DGPU" -> LaptopFeatureCode.DEDICATED_GPU;
                case "LAP-FTR-LTE" -> LaptopFeatureCode.CELLULAR;
                case "LAP-FTR-OLED" -> LaptopFeatureCode.OLED;
                case "LAP-FTR-NUM" -> LaptopFeatureCode.NUMPAD;
                case "LAP-FTR-TB" -> LaptopFeatureCode.THUNDERBOLT;
                case "LAP-FTR-SD" -> LaptopFeatureCode.SD_CARD;
                default -> throw new IllegalArgumentException("unsupported feature item code");
            };
        }
    }
}
