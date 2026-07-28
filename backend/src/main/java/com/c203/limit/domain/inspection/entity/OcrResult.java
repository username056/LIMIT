package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "ocr_result",
    indexes = @Index(name = "idx_ocr_result_evidence", columnList = "evidence_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OcrResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ocr_result_id")
    private Long id;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private OcrFieldType fieldType;

    @Lob
    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "parsed_value", length = 200)
    private String parsedValue;

    /** 확신도 0.000 ~ 1.000 */
    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "ocr_model_version", nullable = false, length = 30)
    private String ocrModelVersion;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Builder
    private OcrResult(Long evidenceId, OcrFieldType fieldType, String rawText, String parsedValue,
                      BigDecimal confidence, String ocrModelVersion, LocalDateTime detectedAt) {
        this.evidenceId = evidenceId;
        this.fieldType = fieldType;
        this.rawText = rawText;
        this.parsedValue = parsedValue;
        this.confidence = confidence;
        this.ocrModelVersion = ocrModelVersion;
        this.detectedAt = detectedAt;
    }

    /** confidence 임계값 기반 자동 통과 로직은 두지 않는다 — 프론트에서 낮은 값 표시만 담당. */
    public boolean isLowConfidence(BigDecimal threshold) {
        return confidence == null || confidence.compareTo(threshold) < 0;
    }

    /** 판매자가 인식값을 직접 고칠 때 사용한다. 별도 이력 테이블 없이 이 값을 그대로 덮어쓴다. */
    public void correctValue(String value) {
        this.parsedValue = value;
    }
}
