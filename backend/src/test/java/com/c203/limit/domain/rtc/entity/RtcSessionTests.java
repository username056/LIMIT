package com.c203.limit.domain.rtc.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.rtc.domain.ConnectionType;
import com.c203.limit.domain.rtc.domain.RtcEndReason;
import com.c203.limit.domain.rtc.domain.RtcSessionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

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
}
