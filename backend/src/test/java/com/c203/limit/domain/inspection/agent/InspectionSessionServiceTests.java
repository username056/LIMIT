package com.c203.limit.domain.inspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.AutomationType;
import com.c203.limit.domain.inspection.repository.ListingChecklistItemRepository;
import com.c203.limit.domain.inspection.service.BatteryReportParsingService;
import com.c203.limit.domain.inspection.service.DxdiagParsingService;
import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.repository.ListingRepository;
import com.c203.limit.domain.product.service.EvidenceUploadService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionSessionServiceTests {
    private InspectionSessionService service;
    private InspectionSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        ListingRepository listingRepository = mock(ListingRepository.class);
        ListingChecklistItemRepository checklistItemRepository =
                mock(ListingChecklistItemRepository.class);
        Listing listing = mock(Listing.class);
        ListingChecklistItem dxdiagItem = mock(ListingChecklistItem.class);

        when(listingRepository.findByIdAndSellerIdAndDeletedAtIsNull(1001L, 10L))
                .thenReturn(Optional.of(listing));
        when(checklistItemRepository.findByListingIdOrderByDisplayOrderAsc(1001L))
                .thenReturn(List.of(dxdiagItem));
        when(dxdiagItem.getAutomationType()).thenReturn(AutomationType.FILE_PARSE);
        when(dxdiagItem.getParserType()).thenReturn("DXDIAG");

        sessionRepository = mock(InspectionSessionRepository.class);
        when(sessionRepository.save(any(InspectionSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.existsByPairingCodeHashAndStatusAndExpiresAtAfter(
                        any(byte[].class), eq(InspectionSessionStatus.CREATED), any()))
                .thenReturn(false);

        service = new InspectionSessionService(
                sessionRepository,
                listingRepository,
                checklistItemRepository,
                mock(EvidenceUploadService.class),
                mock(DxdiagParsingService.class),
                mock(BatteryReportParsingService.class),
                Clock.fixed(Instant.parse("2026-08-03T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void 판매자가_만든_연결_코드를_에이전트_토큰으로_교환한다() {
        var created = service.create(10L, 1001L);
        InspectionSession stored = captureCreatedSession(created.sessionKey());
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findById(created.sessionKey())).thenReturn(Optional.of(stored));

        var paired = service.pair(created.pairingCode(), "0.1.0");

        assertThat(paired.sessionKey()).isEqualTo(created.sessionKey());
        assertThat(paired.agentToken()).isNotBlank();
        assertThat(service.status(10L, created.sessionKey()).status())
                .isEqualTo(InspectionSessionStatus.PAIRED);
    }

    @Test
    void 잘못된_연결_코드는_거부한다() {
        service.create(10L, 1001L);
        when(sessionRepository
                        .findFirstByPairingCodeHashAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                                any(byte[].class),
                                eq(InspectionSessionStatus.CREATED),
                                any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pair("999999", "0.1.0"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INSPECTION_PAIRING_INVALID));
    }

    private InspectionSession captureCreatedSession(String sessionKey) {
        var captor = org.mockito.ArgumentCaptor.forClass(InspectionSession.class);
        org.mockito.Mockito.verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSessionKey()).isEqualTo(sessionKey);
        return captor.getValue();
    }
}
