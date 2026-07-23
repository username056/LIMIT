package com.c203.limit.domain.admin.service;

import com.c203.limit.domain.admin.dto.request.CreateAdminAccountRequest;
import com.c203.limit.domain.admin.dto.request.UpdateAdminAccountAccessRequest;
import com.c203.limit.domain.admin.dto.response.AdminAccountResponse;
import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountManagementService {
    private static final Set<String> ROLES = Set.of("OPERATOR", "SUPER_ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED");
    private final AdminAccountRepository accounts;
    private final AdminActionLogRepository logs;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountManagementService(
            AdminAccountRepository accounts,
            AdminActionLogRepository logs,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.logs = logs;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAccountResponse> list(Integer page, Integer size) {
        var result =
                accounts.findAll(
                        PageRequest.of(
                                page == null ? 0 : Math.max(0, page),
                                size == null ? 20 : Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(
                result.stream().map(this::response).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional
    public AdminAccountResponse create(Long actorId, CreateAdminAccountRequest request) {
        String email = normalizeEmail(request.email());
        validateRole(request.role());
        if (request.password() == null || request.password().length() < 12)
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        if (request.name() == null || request.name().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (accounts.findByEmailIgnoreCase(email).isPresent())
            throw new BusinessException(ErrorCode.ADMIN_EMAIL_DUPLICATED);
        AdminAccount account =
                accounts.save(
                        AdminAccount.createInitial(
                                email,
                                passwordEncoder.encode(request.password()),
                                request.name().trim(),
                                request.role()));
        audit(actorId, "ADMIN_ACCOUNT_CREATE", account.getId(), request.role());
        return response(account);
    }

    @Transactional
    public AdminAccountResponse update(
            Long actorId, Long adminId, UpdateAdminAccountAccessRequest request) {
        AdminAccount account =
                accounts.findById(adminId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        String role = blankToNull(request.role());
        String status = blankToNull(request.status());
        if (role == null && status == null)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if (role != null) validateRole(role);
        if (status != null && !STATUSES.contains(status))
            throw new BusinessException(ErrorCode.INVALID_ADMIN_STATUS);
        boolean removesActiveSuperAdmin =
                "SUPER_ADMIN".equals(account.getRole())
                        && "ACTIVE".equals(account.getStatus())
                        && ("OPERATOR".equals(role) || "SUSPENDED".equals(status));
        if (removesActiveSuperAdmin && accounts.findActiveSuperAdminsForUpdate().size() <= 1) {
            throw new BusinessException(ErrorCode.LAST_SUPER_ADMIN);
        }
        account.updateAccess(role, status);
        accounts.flush();
        audit(actorId, "ADMIN_ACCOUNT_ACCESS_CHANGE", adminId, role + ":" + status);
        return response(account);
    }

    private void validateRole(String role) {
        if (!ROLES.contains(role)) throw new BusinessException(ErrorCode.INVALID_ADMIN_ROLE);
    }

    private String normalizeEmail(String email) {
        if (email == null || !email.contains("@"))
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AdminAccountResponse response(AdminAccount account) {
        return new AdminAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getName(),
                account.getRole(),
                account.getStatus(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }

    private void audit(Long actorId, String action, Long targetId, String reason) {
        logs.save(AdminActionLog.of(actorId, action, "ADMIN_ACCOUNT", targetId, reason));
    }
}
