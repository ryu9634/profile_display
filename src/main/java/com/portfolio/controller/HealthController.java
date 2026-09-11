package com.portfolio.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 배포 스크립트와 로드밸런서가 확인하는 상태 점검 엔드포인트입니다.
 *
 * 애플리케이션이 떴는지뿐 아니라 DB 연결까지 확인해서, 재시작 직후
 * "포트는 열렸지만 DB 는 죽은" 상태를 정상으로 오해하지 않도록 합니다.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());

        try (Connection conn = dataSource.getConnection()) {
            result.put("database", "UP");
        } catch (Exception e) {
            result.put("database", "DOWN");
            result.put("status", "DEGRADED");
        }
        return ResponseEntity.ok(result);
    }
}
