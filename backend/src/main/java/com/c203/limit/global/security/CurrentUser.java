package com.c203.limit.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;

@Component
public class CurrentUser {
    public AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
    public Long memberId() {
        AuthenticatedUser user = require();
        if (!"MEMBER".equals(user.accountType())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return user.id();
    }
    public Long adminId() {
        AuthenticatedUser user = require();
        if (!"ADMIN".equals(user.accountType())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return user.id();
    }
}
