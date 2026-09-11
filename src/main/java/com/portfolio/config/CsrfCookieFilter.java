package com.portfolio.config;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * CSRF 토큰을 매 요청마다 실제로 만들어 XSRF-TOKEN 쿠키가 내려가게 합니다.
 *
 * CookieCsrfTokenRepository 는 토큰을 "읽을 때" 비로소 생성하고 쿠키를 내려줍니다.
 * 화면이 정적 HTML 이라 서버 템플릿에서 토큰을 꺼내 쓸 일이 없고, 그러면 쿠키가
 * 만들어지지 않아 자바스크립트가 토큰을 읽을 수 없습니다.
 * 이 필터가 토큰을 한 번 건드려서 쿠키를 보장합니다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
