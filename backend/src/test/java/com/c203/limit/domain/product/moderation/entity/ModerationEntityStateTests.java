package com.c203.limit.domain.product.moderation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ModerationEntityStateTests {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 0, 0);

    @Test
    void reviewedReportCannotBeReviewedAgainAndPendingReportCannotBeAcknowledged() {
        ListingReport reviewed = report();
        reviewed.decide(ModerationDecision.DISMISS, 9L, null, NOW);

        assertThat(reviewed.getAdminNote()).isNull();
        assertThatThrownBy(() -> reviewed.decide(
                        ModerationDecision.WARN, 9L, "again", NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_ALREADY_REVIEWED));
        assertThatThrownBy(() -> report().acknowledge(NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MODERATION_STATE_CONFLICT));
    }

    @Test
    void reviewedRestorationCannotBeReviewedAgain() {
        ListingRestorationRequest request =
                ListingRestorationRequest.create(1001L, 55L, "fixed");
        request.decide(RestorationDecision.REJECT, 9L, " ", NOW);

        assertThat(request.getReviewNote()).isNull();
        assertThatThrownBy(() -> request.decide(
                        RestorationDecision.APPROVE, 9L, "again", NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESTORATION_REQUEST_ALREADY_REVIEWED));
    }

    @Test
    void resolvedRiskCannotBeRefreshedOrResolvedAgain() {
        ModerationRiskSignal signal = ModerationRiskSignal.create(
                55L,
                1001L,
                null,
                ModerationRiskType.PUBLISHING_VELOCITY,
                -1,
                "risk",
                "risk:55");
        assertThat(signal.getScore()).isZero();
        signal.refresh(200, "higher risk");
        assertThat(signal.getScore()).isEqualTo(100);
        signal.resolve(9L, " ", NOW);
        signal.refresh(50, "ignored");

        assertThat(signal.getScore()).isEqualTo(100);
        assertThat(signal.getResolutionNote()).isNull();
        assertThatThrownBy(() -> signal.resolve(9L, "again", NOW))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MODERATION_STATE_CONFLICT));
    }

    private ListingReport report() {
        return ListingReport.create(
                1001L, 77L, ListingReportCategory.OTHER, "report detail");
    }
}
