package com.portfolio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 시도 제한 규칙.
 *
 * 시계를 직접 옮겨 가며 "15분 뒤 잠김이 풀린다" 같은 시간 조건을 검증합니다.
 */
class LoginAttemptServiceTest {

    /** 테스트가 원하는 시점으로 옮길 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    @DisplayName("실패가 한도에 못 미치면 잠기지 않는다")
    void notBlockedBelowThreshold() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.recordFailure("ip:1.1.1.1");
        }

        assertThat(service.isBlocked("ip:1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("실패가 한도에 닿으면 잠긴다")
    void blockedAtThreshold() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("ip:1.1.1.1");
        }

        assertThat(service.isBlocked("ip:1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("한도 직전에 성공하면 실패 기록이 초기화된다")
    void successResetsCounter() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.recordFailure("ip:1.1.1.1");
        }
        service.recordSuccess("ip:1.1.1.1");

        // 초기화됐으므로 다시 한도 직전까지 실패해도 잠기지 않아야 합니다.
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.recordFailure("ip:1.1.1.1");
        }
        assertThat(service.isBlocked("ip:1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("잠김 시간이 지나면 저절로 풀린다")
    void lockExpires() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("ip:1.1.1.1");
        }
        assertThat(service.isBlocked("ip:1.1.1.1")).isTrue();

        clock.advance(LoginAttemptService.LOCK_DURATION.plusSeconds(1));

        assertThat(service.isBlocked("ip:1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("잠김 직전까지는 풀리지 않는다")
    void lockHoldsUntilDurationPasses() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("ip:1.1.1.1");
        }

        clock.advance(LoginAttemptService.LOCK_DURATION.minusSeconds(1));

        assertThat(service.isBlocked("ip:1.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("오래 전 실패는 세지 않고 처음부터 다시 센다")
    void staleFailuresDoNotAccumulate() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.recordFailure("ip:1.1.1.1");
        }

        // 카운터 초기화 기준(30분)을 넘긴 뒤의 실패는 1회차로 취급됩니다.
        clock.advance(Duration.ofMinutes(31));
        service.recordFailure("ip:1.1.1.1");

        assertThat(service.isBlocked("ip:1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("키가 다르면 서로 영향을 주지 않는다")
    void keysAreIndependent() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("ip:1.1.1.1");
        }

        assertThat(service.isBlocked("ip:1.1.1.1")).isTrue();
        assertThat(service.isBlocked("ip:2.2.2.2")).isFalse();
        assertThat(service.isBlocked("user:admin")).isFalse();
    }

    @Test
    @DisplayName("남은 잠김 시간은 최소 1분으로 안내한다")
    void remainingMinutesIsAtLeastOne() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("ip:1.1.1.1");
        }
        clock.advance(LoginAttemptService.LOCK_DURATION.minusSeconds(5));

        assertThat(service.remainingLockMinutes("ip:1.1.1.1")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("키가 비어 있으면 아무 일도 하지 않는다")
    void nullKeyIsIgnored() {
        service.recordFailure(null);
        service.recordSuccess(null);

        assertThat(service.isBlocked(null)).isFalse();
    }
}
