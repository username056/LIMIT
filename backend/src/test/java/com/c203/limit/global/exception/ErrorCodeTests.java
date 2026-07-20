package com.c203.limit.global.exception;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTests {

    @Test
    void errorCodesUseStableUniqueFormat() {
        List<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();

        assertThat(codes)
                .allMatch(code -> code.matches("^[A-Z]{2,5}\\d{3}$"))
                .doesNotHaveDuplicates();
        assertThat(codes).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void commonErrorCodesKeepReservedNumbers() {
        assertThat(ErrorCode.VALIDATION_FAILED.getCode()).isEqualTo("CMN001");
        assertThat(ErrorCode.INTERNAL_ERROR.getCode()).isEqualTo("CMN002");
        assertThat(ErrorCode.INVALID_INPUT_VALUE.getCode()).isEqualTo("CMN003");
        assertThat(ErrorCode.INVALID_TYPE_VALUE.getCode()).isEqualTo("CMN004");
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo("CMN006");
    }
}
