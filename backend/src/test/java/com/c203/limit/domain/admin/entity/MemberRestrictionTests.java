package com.c203.limit.domain.admin.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberRestrictionTests {
    @Test
    void rejectsInvalidPeriod() {
        var member = Member.createLocal("u@example.com", "p", "runner", null);
        var now = LocalDateTime.now();

        assertThatThrownBy(
                        () ->
                                MemberRestriction.create(
                                        member,
                                        "LOGIN",
                                        "POLICY",
                                        "reason",
                                        now,
                                        now.minusDays(1),
                                        1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_RESTRICTION_PERIOD));
    }

    @Test
    void activeRestrictionCanBeReleased() {
        var member = Member.createLocal("u@example.com", "p", "runner", null);
        var now = LocalDateTime.now();
        var restriction =
                MemberRestriction.create(
                        member, "LOGIN", "POLICY", "reason", now, now.plusDays(1), 1L);

        restriction.release(2L, "false positive");

        assertThat(restriction.getStatus()).isEqualTo("RELEASED");
        assertThat(restriction.getReleasedBy()).isEqualTo(2L);
    }
}
