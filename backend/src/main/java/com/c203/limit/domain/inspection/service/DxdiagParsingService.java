package com.c203.limit.domain.inspection.service;

import com.c203.limit.domain.inspection.entity.Evidence;
import com.c203.limit.domain.inspection.entity.DxdiagResult;
import com.c203.limit.domain.inspection.enums.EvidenceProcessingStatus;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import com.c203.limit.domain.inspection.enums.ParseStatus;
import com.c203.limit.domain.inspection.parser.DxdiagParseException;
import com.c203.limit.domain.inspection.parser.DxdiagParseResult;
import com.c203.limit.domain.inspection.parser.DxdiagXmlParser;
import com.c203.limit.domain.inspection.repository.DxdiagResultRepository;
import com.c203.limit.domain.inspection.repository.EvidenceRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 검수 증거로 업로드된 DxDiag.xml을 다운로드해 DOM으로 파싱하고 dxdiag_result에 저장하는 유스케이스.
 * manufacturer/model/osVersion은 dxdiag_result 테이블에 컬럼이 없어 저장하지 않고 응답에만 담는다.
 */
@Service
public class DxdiagParsingService {

    private static final String PARSER_VERSION = "dxdiag-dom-v1";

    private final EvidenceRepository evidenceRepository;
    private final DxdiagResultRepository dxdiagResultRepository;
    private final DxdiagFileFetcher dxdiagFileFetcher;
    private final DxdiagXmlParser dxdiagXmlParser;

    public DxdiagParsingService(
            EvidenceRepository evidenceRepository,
            DxdiagResultRepository dxdiagResultRepository,
            DxdiagFileFetcher dxdiagFileFetcher,
            DxdiagXmlParser dxdiagXmlParser) {
        this.evidenceRepository = evidenceRepository;
        this.dxdiagResultRepository = dxdiagResultRepository;
        this.dxdiagFileFetcher = dxdiagFileFetcher;
        this.dxdiagXmlParser = dxdiagXmlParser;
    }

    public DxdiagParsingResult parse(Long evidenceId) {
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

        byte[] xmlBytes = dxdiagFileFetcher.fetch(evidence.getCdnUrl());

        DxdiagParseResult parsed;
        DxdiagResult entity;
        try {
            parsed = dxdiagXmlParser.parse(xmlBytes);
            entity =
                    DxdiagResult.builder()
                            .evidenceId(evidenceId)
                            .cpu(parsed.cpu())
                            .memory(parsed.memory())
                            .gpu(parsed.gpu())
                            .gpuMemory(parsed.gpuMemory())
                            .driverVersion(parsed.driverVersion())
                            .soundDevice(parsed.soundDevice())
                            .parserVersion(PARSER_VERSION)
                            .parseStatus(parsed.isComplete() ? ParseStatus.SUCCESS : ParseStatus.PARTIAL)
                            .parsedAt(LocalDateTime.now())
                            .build();
        } catch (DxdiagParseException exception) {
            parsed = null;
            entity = DxdiagResult.failed(evidenceId, PARSER_VERSION);
        }

        DxdiagResult saved = dxdiagResultRepository.save(entity);
        return new DxdiagParsingResult(saved, parsed);
    }

    /** 저장된 엔티티와, 엔티티에는 없는 manufacturer/model/osVersion을 함께 응답으로 전달하기 위한 래퍼. */
    public record DxdiagParsingResult(DxdiagResult entity, DxdiagParseResult parsed) {}
}
