package com.c203.limit.global.config;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationTimeZoneConfig {
    private final ZoneId zoneId;

    public ApplicationTimeZoneConfig(
            @Value("${limit.time-zone:Asia/Seoul}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    @PostConstruct
    void configureDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
    }
}
