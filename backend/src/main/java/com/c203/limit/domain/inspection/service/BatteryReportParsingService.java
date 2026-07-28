package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.entity.BatteryReportResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.mapper.BatteryReportResultMapper;
import com.c203.limit.domain.inspection.parser.BatteryReportHtmlParser;
import com.c203.limit.domain.inspection.parser.BatteryReportParseException;
import com.c203.limit.domain.inspection.parser.BatteryReportParseResult;
import com.c203.limit.domain.inspection.repository.BatteryReportResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** 검수 증거로 업로드된 배터리 리포트 HTML을 다운로드해 파싱하고 battery_report_result에 저장하는 유스케이스. */
@Service
public class BatteryReportParsingService {

    private static final String PARSER_VERSION = "battery-report-v1";

    private final EvidenceRepository evidenceRepository;
    private final BatteryReportResultRepository batteryReportResultRepository;
    private final ListingOwnerReader listingOwnerReader;
    private final BatteryReportFileFetcher batteryReportFileFetcher;
    private final BatteryReportHtmlParser batteryReportHtmlParser;

    public BatteryReportParsingService(
            EvidenceRepository evidenceRepository,
            BatteryReportResultRepository batteryReportResultRepository,
            ListingOwnerReader listingOwnerReader,
            BatteryReportFileFetcher batteryReportFileFetcher,
            BatteryReportHtmlParser batteryReportHtmlParser) {
        this.evidenceRepository = evidenceRepository;
        this.batteryReportResultRepository = batteryReportResultRepository;
        this.listingOwnerReader = listingOwnerReader;
        this.batteryReportFileFetcher = batteryReportFileFetcher;
        this.batteryReportHtmlParser = batteryReportHtmlParser;
    }

    public BatteryReportResult parse(Long evidenceId, Long sellerId) {
        Evidence evidence =
                evidenceRepository
                        .findById(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));

        verifyOwnership(evidence, sellerId);

        if (evidence.getEvidenceType() != EvidenceType.DIAGNOSTIC_FILE
                || !isHtmlMimeType(evidence.getMimeType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }
        if (evidence.getProcessingStatus() != EvidenceProcessingStatus.READY
                || evidence.getCdnUrl() == null) {
            throw new BusinessException(ErrorCode.EVIDENCE_NOT_READY);
        }
        if (batteryReportResultRepository.existsByEvidenceId(evidenceId)) {
            throw new BusinessException(ErrorCode.ALREADY_PARSED);
        }

        byte[] htmlBytes = batteryReportFileFetcher.fetch(evidence.getCdnUrl());

        BatteryReportParseResult parsed;
        try {
            parsed = batteryReportHtmlParser.parse(htmlBytes);
        } catch (BatteryReportParseException exception) {
            throw new BusinessException(ErrorCode.PARSING_FAILED);
        }

        BatteryReportResult entity =
                BatteryReportResultMapper.toEntity(evidenceId, parsed, PARSER_VERSION, LocalDateTime.now());
        return batteryReportResultRepository.save(entity);
    }

    private void verifyOwnership(Evidence evidence, Long sellerId) {
        ListingOwnerReader.ListingOwnerInfo listing =
                listingOwnerReader
                        .findById(evidence.getListingId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));
        if (!listing.sellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isHtmlMimeType(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("html");
    }
}
