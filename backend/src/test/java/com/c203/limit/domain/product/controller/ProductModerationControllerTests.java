package com.c203.limit.domain.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.product.moderation.dto.request.CreateListingReportRequest;
import com.c203.limit.domain.product.moderation.dto.request.CreateRestorationRequest;
import com.c203.limit.domain.product.moderation.dto.response.ListingReportCreatedResponse;
import com.c203.limit.domain.product.moderation.dto.response.ModerationActionResponse;
import com.c203.limit.domain.product.moderation.dto.response.RestorationRequestResponse;
import com.c203.limit.domain.product.moderation.entity.ListingReportCategory;
import com.c203.limit.domain.product.moderation.service.ListingModerationService;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.global.security.CurrentUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductModerationControllerTests {
    @Mock ListingModerationService moderationService;
    @Mock CurrentUser currentUser;
    @Mock SellerStatusReader sellerStatusReader;

    ProductModerationController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductModerationController(
                moderationService, currentUser, sellerStatusReader);
    }

    @Test
    void reportUsesAuthenticatedMemberAndReturnsCreated() {
        CreateListingReportRequest request = new CreateListingReportRequest(
                ListingReportCategory.FRAUD_SUSPECTED, "입금 유도 문구가 있습니다.");
        ListingReportCreatedResponse response = new ListingReportCreatedResponse(
                501L, 1001L, "FRAUD_SUSPECTED", "PENDING", LocalDateTime.now());
        when(currentUser.memberId()).thenReturn(77L);
        when(moderationService.report(77L, 1001L, request)).thenReturn(response);

        var result = controller.report(1001L, request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isSameAs(response);
    }

    @Test
    void warningAcknowledgementRequiresActiveSeller() {
        when(currentUser.memberId()).thenReturn(55L);
        when(moderationService.acknowledgeWarning(55L, 1001L))
                .thenReturn(new ModerationActionResponse(1001L, "NORMAL"));

        var result = controller.acknowledgeWarning(1001L);

        verify(sellerStatusReader).requireActiveSeller(55L);
        assertThat(result.getBody().data().moderationStatus()).isEqualTo("NORMAL");
    }

    @Test
    void restorationRequestRequiresActiveSeller() {
        CreateRestorationRequest request = new CreateRestorationRequest("설명과 사진을 수정했습니다.");
        RestorationRequestResponse response = new RestorationRequestResponse(
                601L,
                1001L,
                55L,
                request.requestNote(),
                "PENDING",
                null,
                null,
                null,
                LocalDateTime.now());
        when(currentUser.memberId()).thenReturn(55L);
        when(moderationService.requestRestoration(55L, 1001L, request)).thenReturn(response);

        var result = controller.requestRestoration(1001L, request);

        verify(sellerStatusReader).requireActiveSeller(55L);
        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().data()).isSameAs(response);
    }
}
