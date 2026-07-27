package com.c203.limit.domain.rtc.repository;

import com.c203.limit.domain.rtc.entity.RtcSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RtcSessionRepository extends JpaRepository<RtcSession, Long> {
    Optional<RtcSession> findByCallAppointmentId(Long callAppointmentId);

    List<RtcSession> findByCallAppointmentIdIn(List<Long> callAppointmentIds);
}
