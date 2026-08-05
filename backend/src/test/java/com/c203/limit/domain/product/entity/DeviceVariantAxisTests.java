package com.c203.limit.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link DeviceVariantAxis#matches}의 타입별 비교 규칙을 검증한다.
 *
 * <p>등록 화면에서 사용자가 고른 값(문자열)을 축의 실제 저장 타입(BigDecimal/Integer/String)과
 * 비교해 같은 조합인지 판단하는 로직이라, 표기 차이(공백·대소문자·소수 자릿수)를 실제로 같은
 * 값으로 인정하는지가 핵심이다.
 */
class DeviceVariantAxisTests {

    private DeviceVariant laptopVariant() {
        DeviceModel model = mock(DeviceModel.class);
        return DeviceVariant.create(model, "NT960XGL-BASE", "갤럭시 북4 울트라")
                .withColor("Graphite")
                .withStorage(512)
                .withLaptopSpecs("Intel Core Ultra 7", "RTX 4060", 16,
                        new BigDecimal("15.60"), new BigDecimal("1.850"))
                .withConnectivity("WIFI");
    }

    @Test
    void valueOfReadsTheColumnMatchingEachAxis() {
        DeviceVariant variant = laptopVariant();

        assertThat(DeviceVariantAxis.SCREEN_SIZE_INCHES.valueOf(variant))
                .isEqualTo(new BigDecimal("15.60"));
        assertThat(DeviceVariantAxis.CPU.valueOf(variant)).isEqualTo("Intel Core Ultra 7");
        assertThat(DeviceVariantAxis.GPU.valueOf(variant)).isEqualTo("RTX 4060");
        assertThat(DeviceVariantAxis.MEMORY_GB.valueOf(variant)).isEqualTo(16);
        assertThat(DeviceVariantAxis.STORAGE_GB.valueOf(variant)).isEqualTo(512);
        assertThat(DeviceVariantAxis.CONNECTIVITY.valueOf(variant)).isEqualTo("WIFI");
        assertThat(DeviceVariantAxis.COLOR.valueOf(variant)).isEqualTo("Graphite");
        assertThat(DeviceVariantAxis.WEIGHT_KG.valueOf(variant)).isEqualTo(new BigDecimal("1.850"));
    }

    @Test
    void bigDecimalAxisMatchesRegardlessOfTrailingZeroFormatting() {
        // 저장된 값은 15.60, 사용자가 고른 문자열은 15.6 — 자릿수 표기가 달라도 같은 값이다.
        DeviceVariant variant = laptopVariant();

        assertThat(DeviceVariantAxis.SCREEN_SIZE_INCHES.matches(variant, "15.6")).isTrue();
        assertThat(DeviceVariantAxis.SCREEN_SIZE_INCHES.matches(variant, "15.60")).isTrue();
        assertThat(DeviceVariantAxis.SCREEN_SIZE_INCHES.matches(variant, "16.0")).isFalse();
    }

    @Test
    void bigDecimalAxisReturnsFalseForNonNumericSelection() {
        DeviceVariant variant = laptopVariant();

        assertThat(DeviceVariantAxis.SCREEN_SIZE_INCHES.matches(variant, "large")).isFalse();
    }

    @Test
    void integerAxisMatchesTrimmedNumericString() {
        DeviceVariant variant = laptopVariant();

        assertThat(DeviceVariantAxis.STORAGE_GB.matches(variant, " 512 ")).isTrue();
        assertThat(DeviceVariantAxis.STORAGE_GB.matches(variant, "256")).isFalse();
    }

    @Test
    void integerAxisReturnsFalseForNonNumericSelection() {
        DeviceVariant variant = laptopVariant();

        assertThat(DeviceVariantAxis.MEMORY_GB.matches(variant, "많이")).isFalse();
    }

    @Test
    void stringAxisMatchesCaseInsensitivelyAndTrimsTheSelectedInput() {
        // matches()는 selected(사용자 입력) 쪽만 trim한다 — 저장된 색상값 자체의 공백까지
        // 정리해 주지는 않으므로, 이 테스트는 selected 쪽 공백만 확인한다.
        DeviceVariant variant = laptopVariant();

        assertThat(DeviceVariantAxis.COLOR.matches(variant, "graphite")).isTrue();
        assertThat(DeviceVariantAxis.COLOR.matches(variant, "GRAPHITE")).isTrue();
        assertThat(DeviceVariantAxis.COLOR.matches(variant, " Graphite ")).isTrue();
        assertThat(DeviceVariantAxis.COLOR.matches(variant, "silver")).isFalse();
    }

    @Test
    void axisWithoutAValueNeverMatches() {
        // 스마트폰 조합에는 cpu·gpu 축 자체가 없다(null). 무엇을 골랐든 일치할 수 없다.
        DeviceModel model = mock(DeviceModel.class);
        DeviceVariant phoneVariant = DeviceVariant.create(model, "SM-S931N-256", "갤럭시 S25 256GB")
                .withStorage(256)
                .withColor("Black");

        assertThat(DeviceVariantAxis.CPU.matches(phoneVariant, "아무거나")).isFalse();
        assertThat(DeviceVariantAxis.SCREEN_SIZE_INCHES.matches(phoneVariant, "6.2")).isFalse();
    }
}
