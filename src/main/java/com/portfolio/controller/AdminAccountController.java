package com.portfolio.controller;

import com.portfolio.dto.ChangeAccountRequest;
import com.portfolio.service.AdminAccountService;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAccount() {
        return ResponseEntity.ok(adminAccountService.getAccountInfo());
    }

    /**
     * 아이디/비밀번호 변경. 아이디가 바뀌면 현재 세션을 끊어 새 아이디로 다시 로그인하게 합니다.
     */
    @PostMapping("/change")
    public ResponseEntity<Map<String, Object>> change(@Valid @RequestBody ChangeAccountRequest request,
                                                      HttpServletRequest httpRequest) {
        String before = SecurityContextHolder.getContext().getAuthentication().getName();
        Map<String, Object> info = new HashMap<>(adminAccountService.changeAccount(request));

        boolean usernameChanged = !before.equals(info.get("username"));
        info.put("reloginRequired", usernameChanged);
        if (usernameChanged) {
            SecurityContextHolder.clearContext();
            if (httpRequest.getSession(false) != null) {
                httpRequest.getSession(false).invalidate();
            }
        }
        return ResponseEntity.ok(info);
    }
}
