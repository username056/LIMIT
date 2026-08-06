package com.c203.limit.domain.product.moderation.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationApiResponseSchemasTests {
    @Test
    void everyOpenApiWrapperRetainsItsTypedData() {
        var reportPage = new ModerationApiResponseSchemas.AdminListingReportPageData(
                List.of(), 0, 20, 0, 0, false);
        var restorationPage = new ModerationApiResponseSchemas.AdminRestorationRequestPageData(
                List.of(), 0, 20, 0, 0, false);
        var riskPage = new ModerationApiResponseSchemas.ModerationRiskSignalPageData(
                List.of(), 0, 20, 0, 0, false);
        var productPage = new ModerationApiResponseSchemas.AdminModeratedProductPageData(
                List.of(), 0, 20, 0, 0, false);

        List<Object> wrappers = List.of(
                new ModerationApiResponseSchemas.ListingReportCreatedApiResponse(null, null),
                new ModerationApiResponseSchemas.ModerationActionApiResponse(null, null),
                new ModerationApiResponseSchemas.RestorationRequestApiResponse(null, null),
                new ModerationApiResponseSchemas.ModerationDashboardApiResponse(null, null),
                new ModerationApiResponseSchemas.AdminListingReportApiResponse(null, null),
                new ModerationApiResponseSchemas.AdminListingReportPageApiResponse(
                        reportPage, null),
                new ModerationApiResponseSchemas.AdminRestorationRequestApiResponse(null, null),
                new ModerationApiResponseSchemas.AdminRestorationRequestPageApiResponse(
                        restorationPage, null),
                new ModerationApiResponseSchemas.ModerationRiskSignalApiResponse(null, null),
                new ModerationApiResponseSchemas.ModerationRiskSignalPageApiResponse(
                        riskPage, null),
                new ModerationApiResponseSchemas.AdminModeratedProductPageApiResponse(
                        productPage, null),
                new ModerationApiResponseSchemas.AdminModeratedProductDetailApiResponse(
                        null, null));

        assertThat(wrappers).hasSize(12);
        assertThat(reportPage.content()).isEmpty();
        assertThat(restorationPage.content()).isEmpty();
        assertThat(riskPage.content()).isEmpty();
        assertThat(productPage.content()).isEmpty();
    }
}
