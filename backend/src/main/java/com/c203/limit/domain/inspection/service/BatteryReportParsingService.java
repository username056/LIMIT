package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.BatteryReportHtmlParser;
import com.c203.limit.domain.inspection.parser.BatteryReportParseException;
import com.c203.limit.domain.inspection.parser.BatteryReportParseResult;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 검수 증거로 업로드된 배터리 리포트 HTML을 다운로드해 DOM으로 파싱하고 battery_report_result에 저장하는 유스케이스. */
@Service
public class BatteryReportParsingService {

    private static final String PARSER_VERSION = "battery-report-dom-v1";

    private final EvidenceRepository evidenceRepository;
    private final BatteryReportResultRepository batteryReportResultRepository;
    private final BatteryReportFileFetcher batteryReportFileFetcher;
    private final BatteryReportHtmlParser batteryReportHtmlParser;

    public BatteryReportParsingService(
            EvidenceRepository evidenceRepository,
            BatteryReportResultRepository batteryReportResultRepository,
            BatteryReportFileFetcher batteryReportFileFetcher,
            BatteryReportHtmlParser batteryReportHtmlParser) {
        this.evidenceRepository = evidenceRepository;
        this.batteryReportResultRepository = batteryReportResultRepository;
        this.batteryReportFileFetcher = batteryReportFileFetcher;
        this.batteryReportHtmlParser = batteryReportHtmlParser;
    }

    public BatteryReportResult parse(Long evidenceId) {
        Evidence evidence =
                evidenceRepository
                        .findById(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));

        if (evidence.getProcessingStatus() != EvidenceProcessingStatus.READY
                || evidence.getCdnUrl() == null) {
            throw new BusinessException(ErrorCode.EVIDENCE_NOT_READY);
        }
        if (evidence.getEvidenceType() != EvidenceType.DIAGNOSTIC_FILE) {
            throw new BusinessException(ErrorCode.INVALID_EVIDENCE_TYPE);
        }

        byte[] htmlBytes = batteryReportFileFetcher.fetch(evidence.getCdnUrl());

        BatteryReportResult entity;
        try {
            BatteryReportParseResult parsed = batteryReportHtmlParser.parse(htmlBytes);
            entity =
                    BatteryReportResult.builder()
                            .evidenceId(evidenceId)
                            .batteryManufacturer(parsed.batteryManufacturer())
                            .designCapacity(parsed.designCapacity())
                            .fullChargeCapacity(parsed.fullChargeCapacity())
                            .cycleCount(parsed.cycleCount())
                            .capacityRatio(parsed.capacityRatio())
                            .parserVersion(PARSER_VERSION)
                            .parseStatus(parsed.isComplete() ? ParseStatus.SUCCESS : ParseStatus.PARTIAL)
                            .parsedAt(LocalDateTime.now())
                            .build();
        } catch (BatteryReportParseException exception) {
            entity = BatteryReportResult.failed(evidenceId, PARSER_VERSION);
        }

        return batteryReportResultRepository.save(entity);
    }
}
