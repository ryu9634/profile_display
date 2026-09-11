package com.portfolio.service;

import com.portfolio.dto.ChangeAccountRequest;
import com.portfolio.model.AdminAccount;
import com.portfolio.repository.AdminAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * DB에 저장된 관리자 계정으로 로그인을 처리하고, 비밀번호/아이디 변경을 담당합니다.
 *
 * 초기화 규칙
 *  - 계정이 없으면(최초 실행) admin.username / admin.password 값으로 생성
 *  - admin.reset-password=true (환경 변수 ADMIN_RESET_PASSWORD=true)로 실행하면
 *    설정값으로 강제 덮어씀 → 비밀번호를 잊었을 때 복구용. 복구 후에는 다시 false로!
 */
@Service
public class AdminAccountService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    private final AdminAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final String initialUsername;
    private final String initialPassword;
    private final boolean resetPassword;

    public AdminAccountService(AdminAccountRepository repository,
                               PasswordEncoder passwordEncoder,
                               LoginAttemptService loginAttemptService,
                               @Value("${admin.username:admin}") String initialUsername,
                               @Value("${admin.password:admin123}") String initialPassword,
                               @Value("${admin.reset-password:false}") boolean resetPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
        this.resetPassword = resetPassword;
    }

    /** 앱 시작 시 호출: 계정이 없으면 생성, 초기화 플래그가 켜져 있으면 설정값으로 되돌림 */
    @Transactional
    public void ensureAccount() {
        AdminAccount account = repository.findById(AdminAccount.SINGLETON_ID).orElse(null);

        if (account == null) {
            account = new AdminAccount();
            account.setId(AdminAccount.SINGLETON_ID);
            account.setUsername(initialUsername);
            account.setPasswordHash(passwordEncoder.encode(initialPassword));
            account.setPasswordChangedAt(System.currentTimeMillis());
            repository.save(account);
            log.info("관리자 계정을 초기 설정값으로 생성했습니다 (username={}). 관리자 화면 > 계정에서 비밀번호를 변경하세요.", initialUsername);
            return;
        }

        if (resetPassword) {
            account.setUsername(initialUsername);
            account.setPasswordHash(passwordEncoder.encode(initialPassword));
            account.setPasswordChangedAt(System.currentTimeMillis());
            repository.save(account);
            log.warn("ADMIN_RESET_PASSWORD 가 켜져 있어 관리자 계정을 설정값으로 초기화했습니다 (username={}). "
                    + "초기화가 끝났으면 반드시 플래그를 끄고 재시작하세요.", initialUsername);
        }
    }

    /**
     * 로그인 시 Spring Security 가 호출합니다.
     *
     * 비밀번호를 비교하기 전에 시도 횟수부터 확인합니다. 여기서 막으면
     * 폼 로그인과 HTTP Basic 두 경로가 함께 보호됩니다.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String ipKey = currentIpKey();
        String userKey = "user:" + username;
        if (loginAttemptService.isBlocked(ipKey) || loginAttemptService.isBlocked(userKey)) {
            long minutes = Math.max(loginAttemptService.remainingLockMinutes(ipKey),
                                    loginAttemptService.remainingLockMinutes(userKey));
            throw new LockedException("로그인 시도가 너무 많습니다. " + minutes + "분 후에 다시 시도해주세요.");
        }

        AdminAccount account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("관리자 계정을 찾을 수 없습니다"));
        return User.builder()
                .username(account.getUsername())
                .password(account.getPasswordHash())
                .roles("ADMIN")
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAccountInfo() {
        AdminAccount account = getAccount();
        return Map.of(
                "username", account.getUsername(),
                "passwordChangedAt", account.getPasswordChangedAt() == null ? 0L : account.getPasswordChangedAt(),
                "usingInitialPassword", passwordEncoder.matches(initialPassword, account.getPasswordHash())
        );
    }

    @Transactional
    public Map<String, Object> changeAccount(ChangeAccountRequest request) {
        AdminAccount account = getAccount();

        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPasswordHash())) {
            throw new RuntimeException("현재 비밀번호가 올바르지 않습니다");
        }

        String newUsername = trimToNull(request.getNewUsername());
        String newPassword = request.getNewPassword() == null || request.getNewPassword().isEmpty()
                ? null : request.getNewPassword();

        if (newUsername == null && newPassword == null) {
            throw new RuntimeException("변경할 아이디 또는 새 비밀번호를 입력해주세요");
        }

        if (newPassword != null) {
            if (newPassword.equals(request.getCurrentPassword())) {
                throw new RuntimeException("새 비밀번호가 현재 비밀번호와 같습니다");
            }
            if (newPassword.trim().length() != newPassword.length()) {
                throw new RuntimeException("비밀번호 앞뒤에 공백을 넣을 수 없습니다");
            }
            if (isTooSimple(newPassword)) {
                throw new RuntimeException("비밀번호는 영문과 숫자를 모두 포함해야 합니다");
            }
            account.setPasswordHash(passwordEncoder.encode(newPassword));
            account.setPasswordChangedAt(System.currentTimeMillis());
        }

        if (newUsername != null && !newUsername.equals(account.getUsername())) {
            account.setUsername(newUsername);
        }

        repository.save(account);
        log.info("관리자 계정 변경: username={}, passwordChanged={}", account.getUsername(), newPassword != null);
        return getAccountInfo();
    }

    /** 현재 요청의 클라이언트 IP. 요청 밖(앱 기동 등)에서 호출되면 null 입니다. */
    private static String currentIpKey() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            HttpServletRequest request =
                    ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return "ip:" + request.getRemoteAddr();
        }
        return null;
    }

    private AdminAccount getAccount() {
        return repository.findById(AdminAccount.SINGLETON_ID)
                .orElseThrow(() -> new RuntimeException("관리자 계정이 없습니다. 서버를 재시작해주세요."));
    }

    private static boolean isTooSimple(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return !(hasLetter && hasDigit);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
