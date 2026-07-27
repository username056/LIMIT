package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * BATTERY_REPORT_SAMPLE_FILE 환경변수로 실제 powercfg /batteryreport HTML 경로를 넘기면 그 파일로
 * 파서를 검증한다. 개인 PC의 실제 진단 파일이라 저장소에는 포함하지 않고, 환경변수가 없으면 스킵된다.
 */
class BatteryReportHtmlParserRealFileIntegrationTests {

    @Test
    void parsesRealBatteryReportSampleFileWhenPathIsProvided() throws IOException {
        String path = System.getenv("BATTERY_REPORT_SAMPLE_FILE");
        Assumptions.assumeTrue(
                path != null && !path.isBlank(), "BATTERY_REPORT_SAMPLE_FILE not set, skipping");

        byte[] bytes = Files.readAllBytes(Path.of(path));
        BatteryReportParseResult result = new BatteryReportHtmlParser().parse(bytes);

        assertThat(result.batteryManufacturer()).isNotBlank();
        assertThat(result.designCapacity()).isNotBlank();
        assertThat(result.fullChargeCapacity()).isNotBlank();
        assertThat(result.cycleCount()).isNotNull();
        assertThat(result.capacityRatio()).isNotNull();
        assertThat(result.isComplete()).isTrue();
    }
}
