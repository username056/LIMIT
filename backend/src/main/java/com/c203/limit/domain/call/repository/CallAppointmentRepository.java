package com.c203.limit.domain.call.repository;

import com.c203.limit.domain.call.domain.AppointmentStatus;
import com.c203.limit.domain.call.entity.CallAppointment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallAppointmentRepository extends JpaRepository<CallAppointment, Long> {
    @Query(
            "SELECT a FROM CallAppointment a WHERE a.proposerId = :memberId OR a.respondentId = :memberId ORDER BY a.id DESC")
    List<CallAppointment> findMine(@Param("memberId") Long memberId);

    List<CallAppointment> findByChatRoomIdAndStatusIn(
            Long chatRoomId, List<AppointmentStatus> statuses);
}
