package com.c203.limit.domain.admin.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_account")
public class AdminAccount extends BaseTimeEntity {
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

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    protected AdminAccount() {}

    public static AdminAccount createInitial(
            String email, String encodedPassword, String name, String role) {
        var account = new AdminAccount();
        account.email = email;
        account.password = encodedPassword;
        account.name = name;
        account.role = role;
        account.status = "ACTIVE";
        account.passwordChangedAt = LocalDateTime.now();
        return account;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void updateAccess(String role, String status) {
        if (role != null) this.role = role;
        if (status != null) this.status = status;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChangedAt = LocalDateTime.now();
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

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }
}
