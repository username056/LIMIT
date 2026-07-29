package com.c203.limit.domain.inspection.checklist;

public enum LaptopFeatureCode {
    PORTS("외부 포트", "지원하는 USB·HDMI 등 각 포트에 장치를 연결해 인식 여부를 확인하세요."),
    RJ45_PORT("유선 LAN(RJ45) 포트", "랜선을 연결해 유선 네트워크 접속과 커넥터 고정 상태를 확인하세요."),
    MICROSD_SLOT("microSD 카드 슬롯", "microSD 카드를 삽입해 카드 인식과 파일 읽기 여부를 확인하세요."),
    CAMERA("내장 카메라", "카메라 앱을 실행해 내장 카메라 영상이 정상적으로 출력되는지 확인하세요."),
    MICROPHONE("내장 마이크", "녹음 앱에서 음성을 녹음하고 입력 게이지와 재생 상태를 확인하세요."),
    SPEAKERS("내장 스피커", "좌우 채널 테스트 음원을 재생해 출력과 잡음 여부를 확인하세요."),
    WIFI("Wi-Fi", "무선 네트워크 연결 후 웹 페이지가 정상적으로 열리는지 확인하세요."),
    BLUETOOTH("Bluetooth", "Bluetooth 장치를 검색하고 실제 연결이 완료되는지 확인하세요."),
    TOUCHSCREEN("터치스크린", "화면 가장자리까지 선을 그어 터치 누락이 없는지 확인하세요."),
    CONVERTIBLE_HINGE("360도 컨버터블 힌지", "노트북 모드에서 태블릿 모드까지 천천히 전환해 힌지와 모드 전환을 확인하세요."),
    STYLUS("스타일러스 펜", "펜으로 선과 글자를 입력해 입력 누락과 필압 인식을 확인하세요."),
    FINGERPRINT("지문 인식", "개인정보가 노출되지 않도록 지문 로그인 성공 여부만 확인하세요."),
    FACE_RECOGNITION("얼굴 인식", "얼굴 정보가 노출되지 않도록 얼굴 로그인 성공 여부만 확인하세요."),
    DEDICATED_GPU("외장 GPU", "시스템 정보에서 외장 GPU 모델과 전용 메모리 인식 여부를 확인하세요."),
    CELLULAR("LTE·5G 셀룰러", "SIM 정보는 가리고 모바일 네트워크 연결 상태를 확인하세요."),
    OLED("OLED 디스플레이", "회색 단색 화면을 전체 화면으로 띄워 번인과 잔상 여부를 확인하세요."),
    NUMPAD("숫자 키패드", "Num Lock을 켜고 숫자와 연산 키가 모두 입력되는지 확인하세요."),
    THUNDERBOLT("Thunderbolt", "호환 장치를 연결해 Thunderbolt 장치 인식 여부를 확인하세요."),
    SD_CARD("SD 카드 리더", "SD 카드를 삽입해 카드 인식과 파일 읽기 여부를 확인하세요.");

    private final String displayNameKo;
    private final String defaultCheckGuideKo;

    LaptopFeatureCode(String displayNameKo, String defaultCheckGuideKo) {
        this.displayNameKo = displayNameKo;
        this.defaultCheckGuideKo = defaultCheckGuideKo;
    }

    public String displayNameKo() {
        return displayNameKo;
    }

    public String defaultCheckGuideKo() {
        return defaultCheckGuideKo;
    }
}
