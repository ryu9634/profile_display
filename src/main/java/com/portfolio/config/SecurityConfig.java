package com.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 관리자 계정은 DB(admin_account)에 저장되며 AdminAccountService 가 UserDetailsService 로 동작합니다.
     * 최초 실행 시 admin.username / admin.password 값으로 생성되고, 이후 관리자 화면에서 변경할 수 있습니다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * API 요청에 대한 인증 실패 처리.
     * - 관리자 화면의 fetch 요청(X-Requested-With 헤더)에는 브라우저 기본 로그인 팝업이 뜨지 않도록
     *   WWW-Authenticate 헤더 없이 401 JSON만 돌려주고, 화면에서 로그인 페이지로 안내합니다.
     * - curl 등 일반 클라이언트에는 기존처럼 HTTP Basic 챌린지를 보냅니다.
     */
    @Bean
    public AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint basic = new BasicAuthenticationEntryPoint();
        basic.setRealmName("Portfolio Admin");
        return (request, response, authException) -> {
            if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"status\":401,\"message\":\"로그인이 필요합니다. 다시 로그인해주세요.\"}");
            } else {
                basic.commence(request, response, authException);
            }
        };
    }

    /**
     * 시도 횟수 초과로 잠긴 상태인지 확인합니다.
     *
     * UserDetailsService 안에서 던진 LockedException 은 DaoAuthenticationProvider 가
     * InternalAuthenticationServiceException 으로 감싸기 때문에, 원인 예외까지 따라가야 합니다.
     */
    private static boolean isLocked(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause()) {
            if (t instanceof LockedException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .antMatchers("/", "/index.html", "/login.html", "/login").permitAll()
                .antMatchers("/styles/**", "/scripts/**").permitAll()
                .antMatchers("/uploads/**").permitAll()
                .antMatchers("/favicon.ico").permitAll()

                .antMatchers(HttpMethod.GET, "/api/health").permitAll()

                .antMatchers(HttpMethod.GET, "/api/categories").permitAll()
                .antMatchers(HttpMethod.GET, "/api/categories/*").permitAll()
                .antMatchers(HttpMethod.GET, "/api/posts").permitAll()
                .antMatchers(HttpMethod.GET, "/api/posts/*").permitAll()
                .antMatchers(HttpMethod.GET, "/api/posts/category/*").permitAll()
                .antMatchers(HttpMethod.GET, "/api/files/*").permitAll()
                .antMatchers(HttpMethod.GET, "/api/settings").permitAll()

                .antMatchers("/admin", "/admin.html").hasRole("ADMIN")
                .antMatchers("/api/account/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.GET, "/api/categories/*/post-count").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/api/settings").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/api/categories/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/api/posts/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.PUT, "/api/posts/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/posts/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.POST, "/api/files/upload").hasRole("ADMIN")
                .antMatchers(HttpMethod.DELETE, "/api/files/*").hasRole("ADMIN")

                .anyRequest().authenticated()
            )

            // API/스크립트 클라이언트용 (기본 엔트리포인트: 브라우저 팝업 대신 401 JSON)
            .httpBasic(basic -> basic.authenticationEntryPoint(apiAuthenticationEntryPoint()))

            // 브라우저(관리자 화면)용 로그인 페이지
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/admin", true)
                // 시도 횟수 초과로 잠긴 경우와 단순 오입력을 구분해서 안내합니다.
                .failureHandler((request, response, exception) -> response.sendRedirect(
                        isLocked(exception) ? "/login.html?locked" : "/login.html?error"))
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessUrl("/login.html?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )

            // CSRF 보호.
            // 화면이 정적 HTML 이라 서버 템플릿에 토큰을 심을 수 없어, 쿠키로 내려주고
            // 자바스크립트가 X-XSRF-TOKEN 헤더에 실어 보냅니다.
            //
            // Authorization 헤더를 직접 붙이는 요청(스크립트의 HTTP Basic 호출)은 제외합니다.
            // 브라우저가 그 헤더를 알아서 붙여 주는 경로가 없으므로 CSRF 대상이 아니고,
            // 매번 토큰을 먼저 받아 오게 하면 API 사용이 불편해집니다.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(request -> request.getHeader("Authorization") != null)
            )
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://cdn.quilljs.com https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                        "style-src 'self' 'unsafe-inline' https://cdn.quilljs.com https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                        "img-src 'self' data: blob: https://img.youtube.com https://i.ytimg.com; " +
                        "font-src 'self' https://fonts.gstatic.com; " +
                        "frame-src 'self' https://www.youtube.com https://www.youtube-nocookie.com; " +
                        "worker-src 'self' blob: https://cdnjs.cloudflare.com")
                )
                .frameOptions().sameOrigin()
            );

        return http.build();
    }
}
