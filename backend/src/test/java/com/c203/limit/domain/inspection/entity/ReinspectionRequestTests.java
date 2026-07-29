package com.c203.limit.domain.inspection.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ReinspectionRequestTests {

    @Test
    void rejectsCompletionWhenRequestIsNotRequested() {
        ReinspectionRequest request =
                ReinspectionRequest.request(1L, 2L, "request-key", "사유", 3L, 4L);
        request.complete();

        assertThatThrownBy(request::complete)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REINSPECTION_INVALID_STATE));
        assertThat(request.getCompletedAt()).isNotNull();
    }
}
