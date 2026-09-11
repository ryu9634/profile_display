package com.portfolio.config;

import com.portfolio.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * 로그인 성공·실패를 {@link LoginAttemptService} 에 기록합니다.
 *
 * 폼 로그인과 HTTP Basic 모두 같은 인증 이벤트를 발생시키므로,
 * 여기 한 곳에서 두 경로를 함께 다룹니다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        loginAttemptService.recordFailure(ipKey(auth));
        loginAttemptService.recordFailure(userKey(auth));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        loginAttemptService.recordSuccess(ipKey(auth));
        loginAttemptService.recordSuccess(userKey(auth));
    }

    private static String ipKey(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof WebAuthenticationDetails) {
            return "ip:" + ((WebAuthenticationDetails) auth.getDetails()).getRemoteAddress();
        }
        return null;
    }

    private static String userKey(Authentication auth) {
        return (auth == null || auth.getName() == null) ? null : "user:" + auth.getName();
    }
}
