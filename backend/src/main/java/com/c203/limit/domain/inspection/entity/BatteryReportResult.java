package com.c203.limit.domain.inspection.entity;

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

    public static BatteryReportResult failed(Long evidenceId, String parserVersion) {
        return BatteryReportResult.builder()
            .evidenceId(evidenceId)
            .parserVersion(parserVersion)
            .parseStatus(ParseStatus.FAILED)
            .parsedAt(LocalDateTime.now())
            .build();
    }
}
