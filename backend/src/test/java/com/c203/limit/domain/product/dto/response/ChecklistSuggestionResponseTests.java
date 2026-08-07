package com.c203.limit.domain.product.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.checklist.ChecklistEvidenceStatus;
import com.c203.limit.domain.inspection.checklist.ChecklistSuggestion;
import org.junit.jupiter.api.Test;

class ChecklistSuggestionResponseTests {

    @Test
    void mapsLegacySuggestionWithoutResolvedFieldsSafely() {
        ChecklistSuggestion suggestion = new ChecklistSuggestion(
                "CAMERA",
                "카메라",
                ChecklistEvidenceStatus.VERIFIED,
                "공식 자료에서 확인했습니다.",
                "실제 동작을 확인하세요.",
                "https://www.example.com/camera",
                "공식 지원 페이지");

        ChecklistSuggestionResponse response = ChecklistSuggestionResponse.from(suggestion);

        assertThat(response.evidenceStatus()).isEqualTo("VERIFIED");
        assertThat(response.evidenceType()).isNull();
        assertThat(response.itemCode()).isNull();
    }
}
