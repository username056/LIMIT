package com.c203.limit.domain.inspection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListingOwnerReaderVisibilityTests {
    @Test
    void onlyPublicLifecycleAndModerationCombinationsAreVisible() {
        assertThat(visibility("ON_SALE", "NORMAL").publiclyVisible()).isTrue();
        assertThat(visibility("ON_SALE", "WARNING_ACK_REQUIRED").publiclyVisible()).isTrue();
        assertThat(visibility("ON_SALE", "SUSPENDED").publiclyVisible()).isFalse();
        assertThat(visibility("DRAFT", "NORMAL").publiclyVisible()).isFalse();
    }

    private ListingOwnerReader.ListingVisibilityInfo visibility(
            String status, String moderationStatus) {
        return new ListingOwnerReader.ListingVisibilityInfo(
                1001L, 55L, status, moderationStatus);
    }
}
