package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * DXDIAG_SAMPLE_FILE 환경변수로 실제 dxdiag /x 출력 XML 경로를 넘기면 그 파일로 파서를 검증한다.
 * 개인 PC의 실제 진단 파일이라 저장소에는 포함하지 않고, 환경변수가 없으면 스킵된다.
 */
class DxdiagXmlParserRealFileIntegrationTests {

    @Test
    void parsesRealDxdiagSampleFileWhenPathIsProvided() throws IOException {
        String path = System.getenv("DXDIAG_SAMPLE_FILE");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "DXDIAG_SAMPLE_FILE not set, skipping");

        byte[] bytes = Files.readAllBytes(Path.of(path));
        DxdiagParseResult result = new DxdiagXmlParser().parse(bytes);

        assertThat(result.manufacturer()).isNotBlank();
        assertThat(result.model()).isNotBlank();
        assertThat(result.osVersion()).contains("Windows");
        assertThat(result.cpu()).isNotBlank();
        assertThat(result.memory()).isNotBlank();
        assertThat(result.gpu()).isNotBlank();
        assertThat(result.gpuMemory()).isNotBlank();
        assertThat(result.driverVersion()).isNotBlank();
        assertThat(result.soundDevice()).isNotBlank();
        assertThat(result.isComplete()).isTrue();
    }
}
