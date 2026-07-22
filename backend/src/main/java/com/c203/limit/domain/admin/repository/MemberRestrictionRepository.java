package com.c203.limit.domain.admin.repository;

import com.c203.limit.domain.admin.entity.MemberRestriction;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRestrictionRepository extends JpaRepository<MemberRestriction, Long> {
    Page<MemberRestriction> findAllByMemberId(Long memberId, Pageable pageable);

    boolean existsByMemberIdAndTypeAndStatus(Long memberId, String type, String status);

    long countByMemberIdAndStatus(Long memberId, String status);
}
