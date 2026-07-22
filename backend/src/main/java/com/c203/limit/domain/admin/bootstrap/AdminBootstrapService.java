package com.c203.limit.domain.admin.bootstrap;

import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ROLES = Set.of("OPERATOR", "SUPER_ADMIN");
    private final AdminAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(AdminAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean ensureInitialAdmin(AdminBootstrapProperties properties) {
        return ensureAccount(
                properties.getEmail(),
                properties.getPassword(),
                properties.getName(),
                properties.getRole());
    }

    @Transactional
    public boolean ensureAccount(String emailValue, String password, String name, String role) {
        String email = normalizeEmail(emailValue);
        if (accounts.findByEmailIgnoreCase(email).isPresent()) return false;
        validate(password, name, role);
        accounts.save(
                AdminAccount.createInitial(
                        email, passwordEncoder.encode(password), name.trim(), role));
        return true;
    }

    private void validate(String password, String name, String role) {
        if (password == null || password.length() < 12) {
            throw new IllegalStateException(
                    "Bootstrap admin password must contain at least 12 characters");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Bootstrap admin name is required");
        }
        if (!ROLES.contains(role)) {
            throw new IllegalStateException("Bootstrap admin role must be OPERATOR or SUPER_ADMIN");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalStateException("INITIAL_ADMIN_EMAIL must be a valid email address");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
