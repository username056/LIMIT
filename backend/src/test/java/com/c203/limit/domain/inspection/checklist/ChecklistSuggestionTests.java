package com.c203.limit.domain.inspection.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.inspection.enums.EvidenceType;
import org.junit.jupiter.api.Test;

/**
 * withEvidenceType()/withItemCode()가 불변 record를 어기지 않고, 지정한 필드만 바꾼
 * 새 인스턴스를 반환하는지 확인한다.
 */
class ChecklistSuggestionTests {

    @Test
    void sevenArgConstructorLeavesEvidenceTypeAndItemCodeNull() {
        ChecklistSuggestion suggestion = new ChecklistSuggestion(
                "FEATURE-1", "지문 인식", ChecklistEvidenceStatus.LIKELY,
                "제조사 스펙에 명시", "지문 센서를 눌러 잠금 해제되는지 확인하세요.",
                "https://example.test/spec", "제조사 공식 스펙");

        assertThat(suggestion.evidenceType()).isNull();
        assertThat(suggestion.itemCode()).isNull();
        assertThat(suggestion.featureCode()).isEqualTo("FEATURE-1");
        assertThat(suggestion.evidenceStatus()).isEqualTo(ChecklistEvidenceStatus.LIKELY);
    }

    @Test
    void withEvidenceTypeReturnsNewInstanceWithoutMutatingTheOriginal() {
        ChecklistSuggestion original = new ChecklistSuggestion(
                "FEATURE-1", "지문 인식", ChecklistEvidenceStatus.LIKELY,
                "제조사 스펙에 명시", "가이드", "https://example.test/spec", "출처");

        ChecklistSuggestion updated = original.withEvidenceType(EvidenceType.PHOTO);

        assertThat(original.evidenceType()).isNull();
        assertThat(updated.evidenceType()).isEqualTo(EvidenceType.PHOTO);
        // evidenceType 외 나머지 필드는 그대로 옮겨진다.
        assertThat(updated.featureCode()).isEqualTo(original.featureCode());
        assertThat(updated.itemCode()).isEqualTo(original.itemCode());
    }

    @Test
    void withItemCodeReturnsNewInstancePreservingPreviouslySetEvidenceType() {
        ChecklistSuggestion withEvidence = new ChecklistSuggestion(
                        "FEATURE-1", "지문 인식", ChecklistEvidenceStatus.LIKELY,
                        "제조사 스펙에 명시", "가이드", "https://example.test/spec", "출처")
                .withEvidenceType(EvidenceType.VIDEO);

        ChecklistSuggestion withItemCode = withEvidence.withItemCode("FP-001");

        assertThat(withItemCode.itemCode()).isEqualTo("FP-001");
        assertThat(withItemCode.evidenceType()).isEqualTo(EvidenceType.VIDEO);
        assertThat(withEvidence.itemCode()).isNull();
    }
}
