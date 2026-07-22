package com.c203.limit.domain.admin.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_account")
public class AdminAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // 운영 스키마에 감사시각 컬럼이 반영되기 전까지 런타임 메타데이터로만 유지한다.
    @Transient private OffsetDateTime updatedAt;

    @Transient private OffsetDateTime lastLoginAt;

    @Transient private OffsetDateTime passwordChangedAt;

    protected AdminAccount() {}

    public static AdminAccount createInitial(
            String email, String encodedPassword, String name, String role) {
        var account = new AdminAccount();
        account.email = email;
        account.password = encodedPassword;
        account.name = name;
        account.role = role;
        account.status = "ACTIVE";
        account.createdAt = OffsetDateTime.now();
        account.updatedAt = account.createdAt;
        account.passwordChangedAt = account.createdAt;
        return account;
    }

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
        this.updatedAt = this.lastLoginAt;
    }

    public void updateAccess(String role, String status) {
        if (role != null) this.role = role;
        if (status != null) this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }
}
