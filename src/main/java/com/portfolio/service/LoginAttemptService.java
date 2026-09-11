package com.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 시도 횟수를 세어 무차별 대입을 막습니다.
 *
 * 관리자 계정이 하나뿐인 서비스라 외부 저장소(Redis 등)를 두지 않고 메모리에 기록합니다.
 * 서버를 재시작하면 기록이 사라지지만, 공격자가 재시작 시점을 맞출 수는 없으므로
 * 이 규모에서는 충분합니다.
 *
 * IP 와 아이디를 각각 셉니다.
 *   - IP 기준: 한 곳에서 여러 아이디를 시도하는 경우를 막습니다.
 *   - 아이디 기준: 여러 IP 에서 한 아이디를 노리는 경우를 막습니다.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** 이 횟수만큼 연속 실패하면 잠깁니다. */
    static final int MAX_ATTEMPTS = 5;

    /** 잠김이 풀리기까지의 시간. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /** 마지막 실패 후 이 시간이 지나면 실패 횟수를 처음부터 다시 셉니다. */
    private static final Duration COUNTER_RESET = Duration.ofMinutes(30);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        int count;
        Instant lastFailure;
    }

    public void recordFailure(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        attempts.compute(key, (k, a) -> {
            if (a == null || Duration.between(a.lastFailure, now).compareTo(COUNTER_RESET) > 0) {
                a = new Attempt();
            }
            a.count++;
            a.lastFailure = now;
            if (a.count == MAX_ATTEMPTS) {
                log.warn("로그인 실패가 {}회 누적되어 {}분간 차단합니다: {}",
                        MAX_ATTEMPTS, LOCK_DURATION.toMinutes(), key);
            }
            return a;
        });
        pruneIfLarge();
    }

    public void recordSuccess(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    public boolean isBlocked(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        Attempt a = attempts.get(key);
        if (a == null || a.count < MAX_ATTEMPTS) {
            return false;
        }
        if (Duration.between(a.lastFailure, Instant.now()).compareTo(LOCK_DURATION) > 0) {
            attempts.remove(key);   // 잠김 시간이 지났으므로 해제
            return false;
        }
        return true;
    }

    /** 잠김이 풀릴 때까지 남은 시간(분). 화면 안내에 씁니다. */
    public long remainingLockMinutes(String key) {
        Attempt a = (key == null) ? null : attempts.get(key);
        if (a == null) {
            return 0;
        }
        long left = LOCK_DURATION.minus(Duration.between(a.lastFailure, Instant.now())).toMinutes();
        return Math.max(1, left);
    }

    /**
     * 무작위 아이디를 대량으로 던져 메모리를 채우는 것을 막기 위해,
     * 항목이 많아지면 이미 만료된 기록을 걷어 냅니다.
     */
    private void pruneIfLarge() {
        if (attempts.size() < 1000) {
            return;
        }
        Instant now = Instant.now();
        attempts.entrySet().removeIf(e ->
                Duration.between(e.getValue().lastFailure, now).compareTo(COUNTER_RESET) > 0);
    }
}
