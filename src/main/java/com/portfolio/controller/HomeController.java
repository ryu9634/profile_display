package com.portfolio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자용 화면 주소.
 *
 * 공개 사이트 주소(/, /카테고리, /카테고리/작품ID)는 메타 태그를 심어야 하므로
 * {@link PublicPageController} 가 따로 처리합니다.
 */
@Controller
public class HomeController {

    /**
     * 관리자 페이지
     */
    @GetMapping("/admin")
    public String admin() {
        return "forward:/admin.html";
    }

    /**
     * 관리자 로그인 페이지 (POST /login 은 Spring Security가 처리)
     */
    @GetMapping("/login")
    public String login() {
        return "forward:/login.html";
    }
}
