package com.c203.limit.domain.rtc.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import com.c203.limit.domain.rtc.entity.RtcSession;
import com.c203.limit.domain.rtc.repository.RtcSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RtcSessionExpirationSchedulerTests {
    @Test
    void expiresSessionsWhoseRejoinGracePeriodHasPassed() {
        RtcSessionRepository repository = mock(RtcSessionRepository.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RtcSessionRepository> provider = mock(ObjectProvider.class);
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().minusMinutes(1));
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findAllByStatusInAndExpiresAtLessThanEqual(any(), any()))
                .thenReturn(List.of(session));

        new RtcSessionExpirationScheduler(provider).expireDueSessions();

        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.EXPIRED);
    }
}
