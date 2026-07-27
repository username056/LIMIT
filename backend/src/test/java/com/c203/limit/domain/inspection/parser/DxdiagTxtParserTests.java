package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DxdiagTxtParserTests {

    private final DxdiagTxtParser parser = new DxdiagTxtParser();

    @Test
    void parsesAllFieldsFromRealisticDxdiagTxtDump() {
        DxdiagParseResult result = parser.parse(bytes(completeDxdiagTxt()));

        assertThat(result.cpu())
                .isEqualTo("11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz (8 CPUs), ~2.8GHz");
        assertThat(result.memory()).isEqualTo("16384 MB RAM");
        assertThat(result.gpu()).isEqualTo("Intel(R) Iris(R) Xe Graphics");
        assertThat(result.gpuMemory()).isEqualTo("8156 MB");
        assertThat(result.driverVersion()).isEqualTo("27.20.100.9415");
        assertThat(result.soundDevice()).isEqualTo("스피커 (Realtek(R) Audio)");
        assertThat(result.isComplete()).isTrue();
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void picksSoundDeviceMarkedAsDefaultPlaybackAmongMultipleDevices() {
        String txt =
                """
                ------------------
                Sound Devices
                ------------------
                      Description: Not Default Speaker
                Default Sound Playback: No
                ------------------
                Sound Devices
                ------------------
                      Description: Default Speaker
                Default Sound Playback: Yes
                """;

        DxdiagParseResult result = parser.parse(bytes(txt));

        assertThat(result.soundDevice()).isEqualTo("Default Speaker");
    }

    @Test
    void returnsPartialResultWhenDisplayDevicesSectionIsMissing() {
        String txt =
                """
                ------------------
                System Information
                ------------------
                      Processor: Test CPU
                        Memory: 8192MB RAM
                ------------------
                Sound Devices
                ------------------
                      Description: Default Speaker
                Default Sound Playback: Yes
                """;

        DxdiagParseResult result = parser.parse(bytes(txt));

        assertThat(result.cpu()).isEqualTo("Test CPU");
        assertThat(result.memory()).isEqualTo("8192 MB RAM");
        assertThat(result.gpu()).isNull();
        assertThat(result.gpuMemory()).isNull();
        assertThat(result.driverVersion()).isNull();
        assertThat(result.isComplete()).isFalse();
        assertThat(result.missingFields()).containsExactlyInAnyOrder("gpu", "gpuMemory", "driverVersion");
    }

    @Test
    void returnsAllNullWhenNoRecognizedSectionsArePresent() {
        String txt = "This is not a dxdiag report at all.";

        DxdiagParseResult result = parser.parse(bytes(txt));

        assertThat(result.missingFields()).hasSize(6);
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void decodesKoreanLocaleTxtDumpSavedInCp949WithoutBom() {
        // 한글 Windows dxdiag는 BOM 없이 시스템 기본 코드페이지(CP949/MS949)로 "모든 정보 저장"을 한다.
        String txt =
                """
                ------------------
                Sound Devices
                ------------------
                      Description: 스피커(Realtek(R) Audio)
                Default Sound Playback: Yes
                """;
        byte[] cp949Bytes = txt.getBytes(Charset.forName("MS949"));

        DxdiagParseResult result = parser.parse(cp949Bytes);

        assertThat(result.soundDevice()).isEqualTo("스피커(Realtek(R) Audio)");
    }

    @Test
    void parsesRealKoreanLocaleDxdiagTxtSample() throws IOException {
        DxdiagParseResult result = parser.parse(readFixture("fixtures/inspection/dxdiag-sample.txt"));

        assertThat(result.cpu()).isEqualTo("13th Gen Intel(R) Core(TM) i7-13700H (20 CPUs), ~2.4GHz");
        assertThat(result.memory()).isEqualTo("32768 MB RAM");
        assertThat(result.gpu()).isEqualTo("Intel(R) Iris(R) Xe Graphics");
        assertThat(result.gpuMemory()).isEqualTo("16291 MB");
        assertThat(result.driverVersion()).isEqualTo("32.0.101.7084");
        assertThat(result.soundDevice()).isEqualTo("헤드폰(Realtek(R) Audio)");
        assertThat(result.isComplete()).isTrue();
        assertThat(result.missingFields()).isEmpty();
    }

    private byte[] bytes(String txt) {
        return txt.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] readFixture(String classpathLocation) throws IOException {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private String completeDxdiagTxt() {
        return """
                ------------------
                System Information
                ------------------
                      Time of this report: 7/27/2026, 19:30:00
                       Machine name: DESKTOP-UB20P0O
                 Operating System: Windows 11 Pro 64-bit (10.0, Build 26100)
                System Manufacturer: SAMSUNG ELECTRONICS CO., LTD.
                       System Model: 950XDB
                          Processor: 11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz (8 CPUs), ~2.8GHz
                             Memory: 16384MB RAM
                ------------------
                Display Devices
                ------------------
                          Card name: Intel(R) Iris(R) Xe Graphics
                      Manufacturer: Intel Corporation
                     Display Memory: 8156 MB
                     Driver Version: 27.20.100.9415
                ------------------
                Sound Devices
                ------------------
                      Description: 스피커 (Realtek(R) Audio)
                Default Sound Playback: Yes
                     Driver Version: 6.0.9200.16384
                """;
    }
}
