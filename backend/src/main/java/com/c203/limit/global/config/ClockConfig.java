package com.c203.limit.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 의존 로직을 테스트에서 결정적으로 검증할 수 있도록 Clock을 빈으로 분리한다. */
@Configuration
public class ClockConfig {
    private final ZoneId zoneId;

    public ClockConfig(@Value("${limit.time-zone:Asia/Seoul}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    /**
     * Clock.systemDefaultZone()은 빈 생성 시점의 JVM 기본 zone을 캡처한다.
     * ApplicationTimeZoneConfig의 @PostConstruct보다 이 빈이 먼저 생성되면 zone이
     * 영구히 어긋나므로, limit.time-zone을 직접 받아 zone을 명시 고정한다.
     */
    @Bean
    Clock clock() {
        return Clock.system(zoneId);
    }
}
