package com.c203.limit.domain.rtc.scheduler;

import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import com.c203.limit.domain.rtc.repository.RtcSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RtcSessionExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(RtcSessionExpirationScheduler.class);

    private final ObjectProvider<RtcSessionRepository> sessionRepositoryProvider;

    public RtcSessionExpirationScheduler(
            ObjectProvider<RtcSessionRepository> sessionRepositoryProvider) {
        this.sessionRepositoryProvider = sessionRepositoryProvider;
    }

    @Scheduled(fixedDelayString = "${limit.rtc.expiration-scan-delay-ms:60000}")
    @Transactional
    public void expireDueSessions() {
        LocalDateTime now = LocalDateTime.now();
        RtcSessionRepository sessionRepository = sessionRepositoryProvider.getIfAvailable();
        if (sessionRepository == null) {
            return;
        }
        var sessions =
                sessionRepository.findAllByStatusInAndExpiresAtLessThanEqual(
                        List.of(RtcSessionStatus.WAITING, RtcSessionStatus.CONNECTED), now);
        long expiredCount = sessions.stream().filter(session -> session.expireIfDue(now)).count();
        if (expiredCount > 0) {
            log.info("RTC sessions expired: count={}", expiredCount);
        }
    }
}
