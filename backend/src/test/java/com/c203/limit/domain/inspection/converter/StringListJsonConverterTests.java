package com.c203.limit.domain.inspection.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** DB 컬럼(JSON 문자열) <-> List<String> 왕복 변환과 null/blank/역직렬화 실패 처리를 확인한다. */
class StringListJsonConverterTests {

    private final StringListJsonConverter converter = new StringListJsonConverter();

    @Test
    void convertToDatabaseColumnReturnsNullForNullAttribute() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumnSerializesListAsJsonArray() {
        String json = converter.convertToDatabaseColumn(List.of("step1", "step2"));

        assertThat(json).isEqualTo("[\"step1\",\"step2\"]");
    }

    @Test
    void convertToDatabaseColumnSerializesEmptyListAsEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
    }

    @Test
    void convertToEntityAttributeReturnsEmptyListForNullColumn() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void convertToEntityAttributeReturnsEmptyListForBlankColumn() {
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void convertToEntityAttributeParsesJsonArrayBackIntoList() {
        assertThat(converter.convertToEntityAttribute("[\"step1\",\"step2\"]"))
                .containsExactly("step1", "step2");
    }

    @Test
    void convertToEntityAttributeThrowsIllegalStateExceptionForMalformedJson() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void roundTripsAListThroughSerializationAndDeserialization() {
        List<String> original = List.of("확인 1", "확인 2", "확인 3");

        String json = converter.convertToDatabaseColumn(original);
        List<String> restored = converter.convertToEntityAttribute(json);

        assertThat(restored).containsExactlyElementsOf(original);
    }
}
