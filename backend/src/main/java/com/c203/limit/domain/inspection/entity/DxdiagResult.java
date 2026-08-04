package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.DiagnosisFieldName;
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

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "os_version", length = 200)
    private String osVersion;

    @Column(name = "storage_capacity", length = 30)
    private String storageCapacity;

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
    private DxdiagResult(Long evidenceId, String modelName, String osVersion, String storageCapacity,
                         String cpu, String memory, String gpu, String gpuMemory,
                         String driverVersion, String soundDevice, String parserVersion,
                         ParseStatus parseStatus, LocalDateTime parsedAt) {
        this.evidenceId = evidenceId;
        this.modelName = modelName;
        this.osVersion = osVersion;
        this.storageCapacity = storageCapacity;
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

    /**
     * 판매자가 필드 하나를 직접 고칠 때 사용한다. 별도 이력 테이블 없이 이 row의 컬럼을 그대로 덮어쓴다.
     *
     * @throws IllegalArgumentException dxdiag_result에 없는 필드명이면
     */
    public void correctField(DiagnosisFieldName fieldName, String value) {
        switch (fieldName) {
            case MODEL_NAME -> this.modelName = value;
            case OS_VERSION -> this.osVersion = value;
            case STORAGE_CAPACITY -> this.storageCapacity = value;
            case CPU -> this.cpu = value;
            case RAM -> this.memory = value;
            case GPU -> this.gpu = value;
            case GPU_MEMORY -> this.gpuMemory = value;
            case DRIVER_VERSION -> this.driverVersion = value;
            case SOUND_DEVICE -> this.soundDevice = value;
            default -> throw new IllegalArgumentException("dxdiag_result has no column for " + fieldName);
        }
    }
}
