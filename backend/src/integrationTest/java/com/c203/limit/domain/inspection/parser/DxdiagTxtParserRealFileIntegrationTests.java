package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * DXDIAG_TXT_SAMPLE_FILE 환경변수로 실제 dxdiag 출력 TXT 경로를 넘기면 그 파일로 파서를 검증한다.
 * 개인 PC의 실제 진단 파일이라 저장소에는 포함하지 않고, 환경변수가 없으면 스킵된다.
 */
class DxdiagTxtParserRealFileIntegrationTests {

    @Test
    void parsesRealDxdiagTxtSampleFileWhenPathIsProvided() throws IOException {
        String path = System.getenv("DXDIAG_TXT_SAMPLE_FILE");
        Assumptions.assumeTrue(path != null && !path.isBlank(), "DXDIAG_TXT_SAMPLE_FILE not set, skipping");

        byte[] bytes = Files.readAllBytes(Path.of(path));
        DxdiagParseResult result = new DxdiagTxtParser().parse(bytes);

        System.out.println("===== DXDIAG TXT PARSE RESULT =====");
        System.out.println("cpu=" + result.cpu());
        System.out.println("memory=" + result.memory());
        System.out.println("gpu=" + result.gpu());
        System.out.println("gpuMemory=" + result.gpuMemory());
        System.out.println("driverVersion=" + result.driverVersion());
        System.out.println("soundDevice=" + result.soundDevice());
        System.out.println("isComplete=" + result.isComplete());

        assertThat(result.cpu()).isNotBlank();
        assertThat(result.memory()).isNotBlank();
        assertThat(result.gpu()).isNotBlank();
    }
}
