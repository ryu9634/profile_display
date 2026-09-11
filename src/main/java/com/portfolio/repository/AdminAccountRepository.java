package com.portfolio.repository;

import com.portfolio.model.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 관리자 계정 저장소. 계정은 항상 한 건만 존재합니다.
 */
@Repository
public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByUsername(String username);
}
