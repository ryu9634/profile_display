package com.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 작가 포트폴리오 사이트의 진입점입니다.
 *
 * 공개 사이트(/)와 관리자 페이지(/admin)를 하나의 Spring Boot 애플리케이션이 함께 제공하며,
 * 화면은 정적 파일(src/main/resources/static)로, 데이터는 REST API(/api/**)로 주고받습니다.
 */
@SpringBootApplication
public class PortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}
