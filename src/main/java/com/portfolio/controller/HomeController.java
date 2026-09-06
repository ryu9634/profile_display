package com.portfolio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /**
     * 루트 경로 - 포트폴리오 메인 페이지
     */
    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }

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
