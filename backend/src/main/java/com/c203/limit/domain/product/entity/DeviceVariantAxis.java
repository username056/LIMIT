package com.c203.limit.domain.product.entity;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * 판매 옵션의 선택 축.
 *
 * <p>등록 화면이 카테고리별로 다른 순서로 축을 묻는다(스마트폰은 색상→용량, 노트북은
 * 화면크기→CPU→GPU→RAM→용량→색상). 축을 enum으로 두면 화면 순서와 무관하게 같은 코드로
 * 좁혀 나갈 수 있고, 응답에 어떤 축이 존재하는지도 데이터로 표현된다.
 */
public enum DeviceVariantAxis {
    SCREEN_SIZE_INCHES(DeviceVariant::getScreenSizeInches),
    CPU(DeviceVariant::getCpu),
    GPU(DeviceVariant::getGpu),
    MEMORY_GB(DeviceVariant::getMemoryGb),
    STORAGE_GB(DeviceVariant::getStorageGb),
    CONNECTIVITY(DeviceVariant::getConnectivity),
    COLOR(DeviceVariant::getColor),
    WEIGHT_KG(DeviceVariant::getWeightKg);

    private final Function<DeviceVariant, Object> reader;

    DeviceVariantAxis(Function<DeviceVariant, Object> reader) {
        this.reader = reader;
    }

    public Object valueOf(DeviceVariant variant) {
        return reader.apply(variant);
    }

    /**
     * 선택값(문자열)과 조합의 값이 같은지 비교한다.
     *
     * <p>숫자 축은 문자열 비교로 판단하지 않는다. "15.6"과 "15.60"은 문자열로는 다르지만 같은
     * 화면 크기이고, DECIMAL로 읽어온 값의 표기가 DB 설정에 따라 달라질 수 있다.
     */
    public boolean matches(DeviceVariant variant, String selected) {
        Object actual = valueOf(variant);
        if (actual == null) return false;
        if (actual instanceof BigDecimal decimal) {
            try {
                return decimal.compareTo(new BigDecimal(selected.trim())) == 0;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        if (actual instanceof Integer number) {
            try {
                return number.intValue() == Integer.parseInt(selected.trim());
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return actual.toString().equalsIgnoreCase(selected.trim());
    }
}
