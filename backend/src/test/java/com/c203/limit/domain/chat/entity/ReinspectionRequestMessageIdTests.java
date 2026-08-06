package com.c203.limit.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReinspectionRequestMessageIdTests {

    @Test
    void isEqualToAnotherIdWithSameRequestIdAndEventType() {
        ReinspectionRequestMessageId id = new ReinspectionRequestMessageId(7L, "REQUESTED");
        ReinspectionRequestMessageId same = new ReinspectionRequestMessageId(7L, "REQUESTED");

        assertThat(id).isEqualTo(same);
        assertThat(id.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    void isEqualToItself() {
        ReinspectionRequestMessageId id = new ReinspectionRequestMessageId(7L, "REQUESTED");

        assertThat(id.equals(id)).isTrue();
    }

    @Test
    void differsWhenEitherComponentDiffers() {
        ReinspectionRequestMessageId id = new ReinspectionRequestMessageId(7L, "REQUESTED");

        assertThat(id).isNotEqualTo(new ReinspectionRequestMessageId(8L, "REQUESTED"));
        assertThat(id).isNotEqualTo(new ReinspectionRequestMessageId(7L, "ACCEPTED"));
        assertThat(id).isNotEqualTo(new ReinspectionRequestMessageId(null, "REQUESTED"));
        assertThat(id).isNotEqualTo(new ReinspectionRequestMessageId(7L, null));
    }

    @Test
    void isNotEqualToNullOrUnrelatedType() {
        ReinspectionRequestMessageId id = new ReinspectionRequestMessageId(7L, "REQUESTED");

        assertThat(id.equals(null)).isFalse();
        assertThat(id.equals("7-REQUESTED")).isFalse();
    }

    @Test
    void treatsDefaultConstructedIdsWithNullComponentsAsEqual() {
        ReinspectionRequestMessageId first = new ReinspectionRequestMessageId();
        ReinspectionRequestMessageId second = new ReinspectionRequestMessageId();

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void behavesAsAStableHashMapKey() {
        Map<ReinspectionRequestMessageId, String> messages = new HashMap<>();
        messages.put(new ReinspectionRequestMessageId(7L, "REQUESTED"), "재검수 요청");

        assertThat(messages).containsEntry(
                new ReinspectionRequestMessageId(7L, "REQUESTED"), "재검수 요청");
        assertThat(messages.get(new ReinspectionRequestMessageId(7L, "ACCEPTED"))).isNull();
    }
}
