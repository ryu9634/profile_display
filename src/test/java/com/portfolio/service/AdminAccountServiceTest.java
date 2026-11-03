package com.portfolio.service;

import com.portfolio.dto.ChangeAccountRequest;
import com.portfolio.model.AdminAccount;
import com.portfolio.repository.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 계정 생성·변경 규칙.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAccountServiceTest {

    private static final String INITIAL_PASSWORD = "initial-pass-1";

    @Mock private AdminAccountRepository repository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AdminAccountService service;
    private AdminAccount account;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(repository, encoder,
                new LoginAttemptService(Clock.systemUTC()),
                "admin", INITIAL_PASSWORD, false);

        account = new AdminAccount();
        account.setId(AdminAccount.SINGLETON_ID);
        account.setUsername("admin");
        account.setPasswordHash(encoder.encode(INITIAL_PASSWORD));
        when(repository.findById(AdminAccount.SINGLETON_ID)).thenReturn(Optional.of(account));
        when(repository.save(any(AdminAccount.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ChangeAccountRequest change(String current, String newUsername, String newPassword) {
        ChangeAccountRequest r = new ChangeAccountRequest();
        r.setCurrentPassword(current);
        r.setNewUsername(newUsername);
        r.setNewPassword(newPassword);
        return r;
    }

    @Test
    @DisplayName("계정이 없으면 설정값으로 만든다")
    void createsAccountWhenMissing() {
        when(repository.findById(AdminAccount.SINGLETON_ID)).thenReturn(Optional.empty());

        service.ensureAccount();

        verify(repository).save(any(AdminAccount.class));
    }

    @Test
    @DisplayName("초기화 플래그가 꺼져 있으면 기존 계정을 건드리지 않는다")
    void doesNotOverwriteExistingAccount() {
        service.ensureAccount();

        verify(repository, never()).save(any(AdminAccount.class));
    }

    @Test
    @DisplayName("초기화 플래그가 켜져 있으면 설정값으로 되돌린다")
    void resetsWhenFlagEnabled() {
        AdminAccountService resetting = new AdminAccountService(repository, encoder,
                new LoginAttemptService(Clock.systemUTC()),
                "admin", INITIAL_PASSWORD, true);
        account.setPasswordHash(encoder.encode("사용자가-바꾼-비밀번호"));

        resetting.ensureAccount();

        assertThat(encoder.matches(INITIAL_PASSWORD, account.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 바꾸지 않는다")
    void rejectsWrongCurrentPassword() {
        assertThatThrownBy(() -> service.changeAccount(change("틀린비번", null, "NewPass123")))
                .hasMessageContaining("현재 비밀번호가 올바르지 않습니다");

        assertThat(encoder.matches(INITIAL_PASSWORD, account.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("영문과 숫자를 모두 포함해야 한다")
    void rejectsPasswordWithoutDigit() {
        assertThatThrownBy(() -> service.changeAccount(change(INITIAL_PASSWORD, null, "onlyletters")))
                .hasMessageContaining("영문과 숫자");
    }

    @Test
    @DisplayName("현재 비밀번호와 같은 값으로는 바꿀 수 없다")
    void rejectsSamePassword() {
        assertThatThrownBy(() -> service.changeAccount(change(INITIAL_PASSWORD, null, INITIAL_PASSWORD)))
                .hasMessageContaining("현재 비밀번호와 같습니다");
    }

    @Test
    @DisplayName("바꿀 항목을 하나도 주지 않으면 거부한다")
    void rejectsEmptyChange() {
        assertThatThrownBy(() -> service.changeAccount(change(INITIAL_PASSWORD, null, null)))
                .hasMessageContaining("변경할 아이디 또는 새 비밀번호");
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 해시가 교체되고 평문은 저장되지 않는다")
    void changesPassword() {
        service.changeAccount(change(INITIAL_PASSWORD, null, "NewPass123"));

        assertThat(encoder.matches("NewPass123", account.getPasswordHash())).isTrue();
        assertThat(account.getPasswordHash()).doesNotContain("NewPass123");
    }

    @Test
    @DisplayName("아이디만 바꿀 수도 있다")
    void changesUsernameOnly() {
        Map<String, Object> info = service.changeAccount(change(INITIAL_PASSWORD, "artist.admin", null));

        assertThat(info.get("username")).isEqualTo("artist.admin");
        assertThat(encoder.matches(INITIAL_PASSWORD, account.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("초기 비밀번호를 그대로 쓰는 중인지 알려준다")
    void reportsInitialPasswordUsage() {
        assertThat(service.getAccountInfo().get("usingInitialPassword")).isEqualTo(true);

        service.changeAccount(change(INITIAL_PASSWORD, null, "NewPass123"));

        assertThat(service.getAccountInfo().get("usingInitialPassword")).isEqualTo(false);
    }
}
