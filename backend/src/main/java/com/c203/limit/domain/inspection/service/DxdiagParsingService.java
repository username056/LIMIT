package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.mapper.DxdiagResultMapper;
import com.c203.limit.domain.inspection.parser.DxdiagParseException;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import com.c203.limit.domain.inspection.parser.DxdiagParser;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.domain.inspection.repository.ListingOwnerReader;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** 검수 증거로 업로드된 DxDiag 진단 파일(txt/xml)을 다운로드해 파싱하고 dxdiag_result에 저장하는 유스케이스. */
@Service
public class DxdiagParsingService {

    private static final String PARSER_VERSION = "dxdiag-v1";

    private final EvidenceRepository evidenceRepository;
    private final DxdiagResultRepository dxdiagResultRepository;
    private final ListingOwnerReader listingOwnerReader;
    private final DxdiagFileFetcher dxdiagFileFetcher;
    private final DxdiagParser dxdiagParser;

    public DxdiagParsingService(
            EvidenceRepository evidenceRepository,
            DxdiagResultRepository dxdiagResultRepository,
            ListingOwnerReader listingOwnerReader,
            DxdiagFileFetcher dxdiagFileFetcher,
            DxdiagParser dxdiagParser) {
        this.evidenceRepository = evidenceRepository;
        this.dxdiagResultRepository = dxdiagResultRepository;
        this.listingOwnerReader = listingOwnerReader;
        this.dxdiagFileFetcher = dxdiagFileFetcher;
        this.dxdiagParser = dxdiagParser;
    }

    public DxdiagResult parse(Long evidenceId, Long sellerId) {
        Evidence evidence =
                evidenceRepository
                        .findById(evidenceId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EVIDENCE_NOT_FOUND));

        verifyOwnership(evidence, sellerId);

        if (evidence.getEvidenceType() != EvidenceType.DIAGNOSTIC_FILE) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_FORMAT);
        }
        if (evidence.getProcessingStatus() != EvidenceProcessingStatus.READY
                || evidence.getCdnUrl() == null) {
            throw new BusinessException(ErrorCode.EVIDENCE_NOT_READY);
        }

        byte[] fileBytes = dxdiagFileFetcher.fetch(evidence.getCdnUrl());

        DxdiagParseResult parsed;
        try {
            parsed = dxdiagParser.parse(fileBytes, evidence.getS3Key(), evidence.getMimeType());
        } catch (DxdiagParseException exception) {
            throw new BusinessException(ErrorCode.PARSING_FAILED);
        }

        DxdiagResult entity =
                DxdiagResultMapper.toEntity(evidenceId, parsed, PARSER_VERSION, LocalDateTime.now());
        return dxdiagResultRepository.save(entity);
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
}
