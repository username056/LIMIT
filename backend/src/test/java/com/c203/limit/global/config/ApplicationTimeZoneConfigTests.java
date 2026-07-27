package com.c203.limit.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class ApplicationTimeZoneConfigTests {

    @Test
    void configuresExplicitApplicationTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            new ApplicationTimeZoneConfig("Asia/Seoul").configureDefaultTimeZone();

            assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
