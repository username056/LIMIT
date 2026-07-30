package com.c203.limit.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class ClockConfigTests {

    @Test
    void clockZoneIsPinnedRegardlessOfJvmDefaultTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            // ApplicationTimeZoneConfig의 @PostConstruct가 아직 실행되기 전 상태를 흉내낸다.
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            Clock clock = new ClockConfig("Asia/Seoul").clock();

            assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
