package com.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시각을 빈으로 제공합니다.
 *
 * 시간에 따라 동작이 달라지는 코드(로그인 잠금 해제 등)가 Instant.now() 를
 * 직접 호출하면 테스트에서 실제 시간을 기다려야 합니다. Clock 을 주입받으면
 * 테스트가 원하는 시점으로 시계를 옮길 수 있습니다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
