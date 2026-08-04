package com.c203.limit.domain.rtc.repository;

import com.c203.limit.domain.rtc.entity.RtcSessionChecklistResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RtcSessionChecklistResultRepository
        extends JpaRepository<RtcSessionChecklistResult, Long> {
    List<RtcSessionChecklistResult> findByRtcSessionId(Long rtcSessionId);

    boolean existsByListingChecklistItemId(Long listingChecklistItemId);
}
