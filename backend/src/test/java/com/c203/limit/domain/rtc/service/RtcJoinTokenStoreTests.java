package com.c203.limit.domain.rtc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RtcJoinTokenStoreTests {
    @Test
    void joinTokenCanBeConsumedOnlyOnce() {
        RtcJoinTokenStore store = new RtcJoinTokenStore();
        var ticket = store.issue(10L, 20L);

        assertThat(store.consume(ticket.token())).isEqualTo(ticket);
        assertThat(store.consume(ticket.token())).isNull();
    }
}
