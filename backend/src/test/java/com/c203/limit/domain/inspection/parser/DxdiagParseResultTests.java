package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** missingFields()/isComplete()가 9개 필드 전부를 실제로 검사하는지 확인한다. */
class DxdiagParseResultTests {

    @Test
    void isCompleteAndHasNoMissingFieldsWhenEveryFieldIsPresent() {
        DxdiagParseResult result = new DxdiagParseResult(
                "960XFH", "Windows 11", "975.7 GB", "i7-1165G7", "16384 MB",
                "Iris Xe", "8156 MB", "27.20.100.9415", "Realtek Audio");

        assertThat(result.isComplete()).isTrue();
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void reportsEachNullFieldByName() {
        DxdiagParseResult result = new DxdiagParseResult(
                null, "Windows 11", null, "i7-1165G7", "16384 MB",
                "Iris Xe", null, "27.20.100.9415", "Realtek Audio");

        assertThat(result.isComplete()).isFalse();
        assertThat(result.missingFields())
                .containsExactly("modelName", "storageCapacity", "gpuMemory");
    }

    @Test
    void allNineFieldsCanBeReportedMissing() {
        DxdiagParseResult result = new DxdiagParseResult(
                null, null, null, null, null, null, null, null, null);

        assertThat(result.missingFields()).hasSize(9);
        assertThat(result.isComplete()).isFalse();
    }
}
