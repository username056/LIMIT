package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ListingSpecSnapshotTests {

    /**
     * null 축은 키 자체를 남기지 않는다. {@code "cpu": null}이 남으면 '값을 모른다'와 '이 기기에는
     * 없는 축이다'를 나중에 구분할 수 없다.
     */
    @Test
    void omitsAbsentAxesInsteadOfWritingNulls() {
        String snapshot =
                ListingSpecSnapshot.of(
                        "Samsung", "Galaxy S24", "SM-S921N", "블랙", 256, null, null, null, null,
                        null);

        assertThat(snapshot).doesNotContain("cpu").doesNotContain("null");
        assertThat(snapshot).contains("\"storageGb\":256").contains("\"color\":\"블랙\"");
    }

    @Test
    void keepsLaptopAxesWhenPresent() {
        String snapshot =
                ListingSpecSnapshot.of(
                        "Samsung",
                        "Galaxy Book4",
                        "NT750XGK",
                        "그레이",
                        512,
                        16,
                        new BigDecimal("15.6"),
                        "Intel Core 5",
                        null,
                        null);

        assertThat(snapshot)
                .contains("\"screenSizeInches\":15.6")
                .contains("\"cpu\":\"Intel Core 5\"")
                .contains("\"memoryGb\":16");
    }

    /** 빈 문자열은 값이 있는 것처럼 보이지만 실제로는 정보가 없으므로 남기지 않는다. */
    @Test
    void treatsBlankTextAsAbsent() {
        String snapshot =
                ListingSpecSnapshot.of(
                        "Samsung", "Galaxy S24", "SM-S921N", "   ", null, null, null, null, null,
                        null);

        assertThat(snapshot).doesNotContain("color");
    }

    @Test
    void returnsNullWhenNothingIsKnown() {
        assertThat(
                        ListingSpecSnapshot.of(
                                null, null, null, null, null, null, null, null, null, null))
                .isNull();
    }
}
