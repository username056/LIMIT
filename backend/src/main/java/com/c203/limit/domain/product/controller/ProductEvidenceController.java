package com.c203.limit.domain.product.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.ConfirmEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.request.CreateRecaptureRequest;
import com.c203.limit.domain.product.dto.response.EvidenceConfirmationResponse;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ProductChecklistItemResponse;
import com.c203.limit.domain.product.dto.response.RecaptureRequestResponse;
import com.c203.limit.global.response.ApiResponse;

@RestController
public class ProductEvidenceController implements ProductEvidenceApi {

    private static final OffsetDateTime CAPTURED_AT = OffsetDateTime.parse("2026-07-22T12:01:00+09:00");
    private static final OffsetDateTime UPLOADED_AT = OffsetDateTime.parse("2026-07-22T12:03:00+09:00");

    @Override
    public ResponseEntity<ApiResponse<List<ProductChecklistItemResponse>>> getProductChecklist(
            Long productId,
            String status,
            boolean requiredOnly
    ) {
        List<ProductChecklistItemResponse> items = List.of(
                new ProductChecklistItemResponse(7001L, "SP-EXT-001", "전면·후면·측면 외관", "PHOTO", true, "COMPLETED", 9001L, 1),
                new ProductChecklistItemResponse(7002L, "SP-DSP-002", "화면 전체 터치", "VIDEO", true, "RECAPTURE_REQUESTED", 9002L, 1)
        );
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @Override
    public ResponseEntity<ApiResponse<EvidenceUploadUrlResponse>> createEvidenceUploadUrl(
            Long productId,
            Long checklistItemId,
            CreateEvidenceUploadUrlRequest request
    ) {
        EvidenceUploadUrlResponse response = new EvidenceUploadUrlResponse(
                "upl_01",
                "products/%d/checklist/%d/attempt-2.mp4".formatted(productId, checklistItemId),
                "https://storage.example.com/presigned",
                OffsetDateTime.parse("2026-07-22T12:15:00+09:00"),
                Map.of("Content-Type", request.getContentType())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<EvidenceResponse>> completeEvidence(
            Long productId,
            Long checklistItemId,
            CompleteEvidenceRequest request
    ) {
        EvidenceResponse response = evidence(9003L, checklistItemId, 2, true, "NONE", request.getCapturedAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidenceHistory(
            Long productId,
            Long checklistItemId
    ) {
        List<EvidenceResponse> history = List.of(
                evidence(9002L, checklistItemId, 1, false, "RECAPTURE_REQUESTED", OffsetDateTime.parse("2026-07-22T11:00:00+09:00")),
                evidence(9003L, checklistItemId, 2, true, "NONE", CAPTURED_AT)
        );
        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    @Override
    public ResponseEntity<ApiResponse<EvidenceConfirmationResponse>> confirmEvidence(
            Long productId,
            Long evidenceId,
            ConfirmEvidenceRequest request
    ) {
        EvidenceConfirmationResponse response = new EvidenceConfirmationResponse(
                evidenceId, request.getStatus(), 77L, OffsetDateTime.parse("2026-07-22T13:00:00+09:00")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<RecaptureRequestResponse>> requestRecapture(
            Long productId,
            Long checklistItemId,
            CreateRecaptureRequest request
    ) {
        RecaptureRequestResponse response = new RecaptureRequestResponse(
                8101L,
                checklistItemId,
                9002L,
                request.getReasonCode(),
                request.getReason(),
                "REQUESTED",
                OffsetDateTime.parse("2026-07-22T13:10:00+09:00")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    private EvidenceResponse evidence(
            Long evidenceId,
            Long checklistItemId,
            int attemptNo,
            boolean isLatest,
            String confirmationStatus,
            OffsetDateTime capturedAt
    ) {
        return new EvidenceResponse(
                evidenceId,
                checklistItemId,
                "VIDEO",
                attemptNo,
                isLatest,
                "https://cdn.example.com/evidence/%d.mp4".formatted(evidenceId),
                "READY",
                confirmationStatus,
                capturedAt == null ? CAPTURED_AT : capturedAt,
                UPLOADED_AT
        );
    }
}
