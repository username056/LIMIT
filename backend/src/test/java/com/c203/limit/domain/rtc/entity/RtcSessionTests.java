package com.c203.limit.domain.rtc.entity;

import static org.assertj.core.api.Assertions.assertThat;
import com.c203.limit.domain.rtc.domain.ConnectionType;
import com.c203.limit.domain.rtc.domain.RtcEndReason;
import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RtcSessionTests {
    @Test
    void recordsConnectionAndEndWithoutRecordingMedia() {
        RtcSession session = RtcSession.waiting(
                1L, 2L, 3L, 4L, 5L, LocalDateTime.now().plusHours(2));

        session.connect(ConnectionType.P2P);
        session.end(RtcEndReason.COMPLETED, "외관과 충전 상태 확인");

        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.ENDED);
        assertThat(session.getConnectedAt()).isNotNull();
        assertThat(session.getEndedAt()).isNotNull();
        assertThat(session.getVerificationMemo()).isEqualTo("외관과 충전 상태 확인");
    }

    @Test
    void marksWaitingSessionExpiredWithoutRecordingMedia() {
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(1);
        RtcSession session =
                RtcSession.waiting(1L, 2L, 3L, 4L, 5L, expiredAt);

        boolean expired = session.expireIfDue(LocalDateTime.now());

        assertThat(expired).isTrue();
        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.EXPIRED);
        assertThat(session.getEndedAt()).isNotNull();
        assertThat(session.isClosed()).isTrue();
    }

    @Test
    void leavesActiveSessionUnchanged() {
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().plusMinutes(1));

        boolean expired = session.expireIfDue(LocalDateTime.now());

        assertThat(expired).isFalse();
        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.WAITING);
    }

    @Test
    void treatsMissingExpirationAsExpired() {
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().plusMinutes(1));
        ReflectionTestUtils.setField(session, "expiresAt", null);

        boolean expired = session.expireIfDue(LocalDateTime.now());

        assertThat(expired).isTrue();
        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.EXPIRED);
        assertThat(session.getEndedAt()).isNotNull();
    }

    @Test
    void keepsDisconnectedSessionJoinableForThirtyMinutes() {
        RtcSession session =
                RtcSession.waiting(
                        1L, 2L, 3L, 4L, 5L, LocalDateTime.now().plusHours(2));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        session.disconnect(RtcEndReason.COMPLETED, "검수 완료", expiresAt);

        assertThat(session.getStatus()).isEqualTo(RtcSessionStatus.WAITING);
        assertThat(session.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(session.isClosed()).isFalse();
    }
}
