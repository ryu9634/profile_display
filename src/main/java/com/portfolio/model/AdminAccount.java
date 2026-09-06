package com.portfolio.model;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 계정. 비밀번호는 BCrypt 해시로만 저장됩니다.
 * 최초 실행 시 admin.username / admin.password 설정값으로 만들어지고,
 * 이후에는 관리자 화면에서 직접 변경할 수 있습니다.
 */
@Entity
@Table(name = "admin_account")
@Data
@NoArgsConstructor
public class AdminAccount {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Long passwordChangedAt;

    @Column(name = "created_at")
    private Long createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }
}
