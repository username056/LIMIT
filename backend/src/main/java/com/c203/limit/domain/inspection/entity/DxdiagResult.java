package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.ParseStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "dxdiag_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DxdiagResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dxdiag_result_id")
    private Long id;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "cpu", length = 100)
    private String cpu;

    @Column(name = "memory", length = 50)
    private String memory;

    @Column(name = "gpu", length = 100)
    private String gpu;

    @Column(name = "gpu_memory", length = 30)
    private String gpuMemory;

    @Column(name = "driver_version", length = 50)
    private String driverVersion;

    @Column(name = "sound_device", length = 100)
    private String soundDevice;

    @Column(name = "parser_version", nullable = false, length = 20)
    private String parserVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    private ParseStatus parseStatus;

    @Column(name = "parsed_at", nullable = false)
    private LocalDateTime parsedAt;

    @Builder
    private DxdiagResult(Long evidenceId, String cpu, String memory, String gpu, String gpuMemory,
                         String driverVersion, String soundDevice, String parserVersion,
                         ParseStatus parseStatus, LocalDateTime parsedAt) {
        this.evidenceId = evidenceId;
        this.cpu = cpu;
        this.memory = memory;
        this.gpu = gpu;
        this.gpuMemory = gpuMemory;
        this.driverVersion = driverVersion;
        this.soundDevice = soundDevice;
        this.parserVersion = parserVersion;
        this.parseStatus = parseStatus;
        this.parsedAt = parsedAt;
    }

    public static DxdiagResult failed(Long evidenceId, String parserVersion) {
        return DxdiagResult.builder()
            .evidenceId(evidenceId)
            .parserVersion(parserVersion)
            .parseStatus(ParseStatus.FAILED)
            .parsedAt(LocalDateTime.now())
            .build();
    }
}
