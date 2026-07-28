package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "battery_report_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatteryReportResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "battery_report_result_id")
    private Long id;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "design_capacity", length = 30)
    private String designCapacity;

    @Column(name = "full_charge_capacity", length = 30)
    private String fullChargeCapacity;

    @Column(name = "cycle_count")
    private Integer cycleCount;

    @Column(name = "battery_manufacturer", length = 50)
    private String batteryManufacturer;

    @Column(name = "capacity_ratio", precision = 5, scale = 2)
    private BigDecimal capacityRatio;

    @Column(name = "parser_version", nullable = false, length = 20)
    private String parserVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    private ParseStatus parseStatus;

    @Column(name = "parsed_at", nullable = false)
    private LocalDateTime parsedAt;

    @Builder
    private BatteryReportResult(Long evidenceId, String designCapacity, String fullChargeCapacity,
                                Integer cycleCount, String batteryManufacturer, BigDecimal capacityRatio,
                                String parserVersion, ParseStatus parseStatus, LocalDateTime parsedAt) {
        this.evidenceId = evidenceId;
        this.designCapacity = designCapacity;
        this.fullChargeCapacity = fullChargeCapacity;
        this.cycleCount = cycleCount;
        this.batteryManufacturer = batteryManufacturer;
        this.capacityRatio = capacityRatio;
        this.parserVersion = parserVersion;
        this.parseStatus = parseStatus;
        this.parsedAt = parsedAt;
    }

    /**
     * 판매자가 필드 하나를 직접 고칠 때 사용한다. 별도 이력 테이블 없이 이 row의 컬럼을 그대로 덮어쓴다.
     *
     * @throws IllegalArgumentException battery_report_result에 없는 필드명이면
     */
    public void correctField(DiagnosisFieldName fieldName, String value) {
        switch (fieldName) {
            case DESIGN_CAPACITY -> this.designCapacity = value;
            case FULL_CHARGE_CAPACITY -> this.fullChargeCapacity = value;
            case CYCLE_COUNT -> this.cycleCount = value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
            case BATTERY_MANUFACTURER -> this.batteryManufacturer = value;
            case CAPACITY_RATIO -> this.capacityRatio = value == null || value.isBlank() ? null : new BigDecimal(value.trim());
            default -> throw new IllegalArgumentException("battery_report_result has no column for " + fieldName);
        }
    }
}
