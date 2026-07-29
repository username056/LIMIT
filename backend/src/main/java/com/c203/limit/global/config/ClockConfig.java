package com.c203.limit.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 의존 로직을 테스트에서 결정적으로 검증할 수 있도록 Clock을 빈으로 분리한다. */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
