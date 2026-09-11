package com.portfolio.controller;

import com.portfolio.dto.SiteSettingsRequest;
import com.portfolio.model.SiteSettings;
import com.portfolio.service.SiteSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 사이트 전역 설정 API.
 *
 * 색상, 폰트, 로고, 연락처처럼 화면 전체에 걸리는 값을 하나의 레코드로 관리합니다.
 * 공개 사이트가 첫 화면을 그릴 때 GET 으로 읽어 가므로 조회는 인증 없이 허용합니다.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SiteSettingsController {

    private final SiteSettingsService siteSettingsService;

    @GetMapping
    public ResponseEntity<SiteSettings> getSettings() {
        return ResponseEntity.ok(siteSettingsService.getSettings());
    }

    @PostMapping
    public ResponseEntity<SiteSettings> updateSettings(@RequestBody SiteSettingsRequest request) {
        SiteSettings updated = siteSettingsService.updateSettings(request);
        return ResponseEntity.ok(updated);
    }
}
