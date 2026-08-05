package com.c203.limit.domain.inspection.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * toEntity()의 상태 판정(SUCCESS/PARTIAL/FAILED 3분기)과 toResponse()가 엔티티의 null 컬럼을
 * missingFields로 되짚어내는지 확인한다.
 */
class DxdiagResultMapperTests {

    private static final LocalDateTime PARSED_AT = LocalDateTime.of(2026, 8, 5, 10, 0);

    @Test
    void marksSuccessWhenNoFieldIsMissing() {
        DxdiagParseResult parsed = new DxdiagParseResult(
                "960XFH", "Windows 11", "975.7 GB", "i7-1165G7", "16384 MB",
                "Iris Xe", "8156 MB", "27.20.100.9415", "Realtek Audio");

        DxdiagResult entity = DxdiagResultMapper.toEntity(1L, parsed, "dxdiag-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(entity.getEvidenceId()).isEqualTo(1L);
        assertThat(entity.getModelName()).isEqualTo("960XFH");
        assertThat(entity.getParserVersion()).isEqualTo("dxdiag-dom-v1");
        assertThat(entity.getParsedAt()).isEqualTo(PARSED_AT);
    }

    @Test
    void marksPartialWhenSomeButNotAllFieldsAreMissing() {
        DxdiagParseResult parsed = new DxdiagParseResult(
                "960XFH", "Windows 11", "975.7 GB", "i7-1165G7", "16384 MB",
                "Iris Xe", null, null, null);

        DxdiagResult entity = DxdiagResultMapper.toEntity(1L, parsed, "dxdiag-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.PARTIAL);
    }

    @Test
    void marksFailedWhenEveryTrackedFieldIsMissing() {
        DxdiagParseResult parsed = new DxdiagParseResult(
                null, null, null, null, null, null, null, null, null);

        DxdiagResult entity = DxdiagResultMapper.toEntity(1L, parsed, "dxdiag-dom-v1", PARSED_AT);

        assertThat(entity.getParseStatus()).isEqualTo(ParseStatus.FAILED);
    }

    @Test
    void toResponseListsMissingColumnsFromTheEntityRatherThanTheOriginalParseResult() {
        // toResponse는 엔티티(판매자가 correctField로 고친 뒤일 수 있음)를 기준으로 다시 계산해야
        // 한다. 즉 파싱 시점엔 없던 값이라도 이후 엔티티에 채워졌으면 missingFields에서 빠져야 한다.
        DxdiagParseResult parsed = new DxdiagParseResult(
                null, "Windows 11", "975.7 GB", "i7-1165G7", "16384 MB",
                "Iris Xe", "8156 MB", "27.20.100.9415", "Realtek Audio");
        DxdiagResult entity = DxdiagResultMapper.toEntity(1L, parsed, "dxdiag-dom-v1", PARSED_AT);
        entity.correctField(com.c203.limit.domain.inspection.enums.DiagnosisFieldName.MODEL_NAME, "960XFH");

        var response = DxdiagResultMapper.toResponse(entity);

        assertThat(response.getModelName()).isEqualTo("960XFH");
        assertThat(response.getMissingFields()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(ParseStatus.PARTIAL);
    }
}
