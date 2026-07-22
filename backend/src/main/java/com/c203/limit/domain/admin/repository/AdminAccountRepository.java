package com.c203.limit.domain.admin.repository;

import com.c203.limit.domain.admin.entity.AdminAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select account from AdminAccount account "
                    + "where account.role = 'SUPER_ADMIN' and account.status = 'ACTIVE' "
                    + "order by account.id")
    List<AdminAccount> findActiveSuperAdminsForUpdate();
}
