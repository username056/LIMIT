package com.c203.limit.domain.inspection.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * correctField()가 dxdiag_result의 9개 컬럼 각각에 정확히 매핑되는지, 그리고 이 엔티티에
 * 없는 필드(배터리 리포트 전용)를 넘기면 거부하는지 확인한다.
 */
class DxdiagResultTests {

    private DxdiagResult newResult() {
        return DxdiagResult.builder()
                .evidenceId(1L)
                .parserVersion("dxdiag-dom-v1")
                .parseStatus(ParseStatus.PARTIAL)
                .parsedAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                .build();
    }

    @Test
    void correctFieldOverwritesTheMatchingColumnOnly() {
        DxdiagResult result = newResult();

        result.correctField(DiagnosisFieldName.MODEL_NAME, "960XFH");
        result.correctField(DiagnosisFieldName.OS_VERSION, "Windows 11");
        result.correctField(DiagnosisFieldName.STORAGE_CAPACITY, "975.7 GB");
        result.correctField(DiagnosisFieldName.CPU, "i7-1165G7");
        result.correctField(DiagnosisFieldName.RAM, "16384 MB");
        result.correctField(DiagnosisFieldName.GPU, "Iris Xe");
        result.correctField(DiagnosisFieldName.GPU_MEMORY, "8156 MB");
        result.correctField(DiagnosisFieldName.DRIVER_VERSION, "27.20.100.9415");
        result.correctField(DiagnosisFieldName.SOUND_DEVICE, "Realtek Audio");

        assertThat(result.getModelName()).isEqualTo("960XFH");
        assertThat(result.getOsVersion()).isEqualTo("Windows 11");
        assertThat(result.getStorageCapacity()).isEqualTo("975.7 GB");
        assertThat(result.getCpu()).isEqualTo("i7-1165G7");
        assertThat(result.getMemory()).isEqualTo("16384 MB");
        assertThat(result.getGpu()).isEqualTo("Iris Xe");
        assertThat(result.getGpuMemory()).isEqualTo("8156 MB");
        assertThat(result.getDriverVersion()).isEqualTo("27.20.100.9415");
        assertThat(result.getSoundDevice()).isEqualTo("Realtek Audio");
    }

    @Test
    void correctingOneFieldDoesNotTouchOthers() {
        DxdiagResult result = newResult();
        result.correctField(DiagnosisFieldName.CPU, "original-cpu");

        result.correctField(DiagnosisFieldName.GPU, "new-gpu");

        assertThat(result.getCpu()).isEqualTo("original-cpu");
        assertThat(result.getGpu()).isEqualTo("new-gpu");
    }

    @Test
    void correctingDoesNotRecomputeParseStatus() {
        // parseStatus는 파싱 시점 값이 그대로 남는다 — correctField는 컬럼값만 덮어쓸 뿐
        // PARTIAL/FAILED였던 상태를 SUCCESS로 승격하지 않는다.
        DxdiagResult result = newResult();

        result.correctField(DiagnosisFieldName.MODEL_NAME, "960XFH");

        assertThat(result.getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
    }

    @Test
    void rejectsFieldNamesThatHaveNoDxdiagColumn() {
        // DESIGN_CAPACITY 등은 배터리 리포트 전용 필드라 dxdiag_result에 대응 컬럼이 없다.
        DxdiagResult result = newResult();

        assertThatThrownBy(() -> result.correctField(DiagnosisFieldName.DESIGN_CAPACITY, "50000 mWh"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DESIGN_CAPACITY");
    }
}
