package com.portfolio.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 누가 무엇을 할 수 있는지에 대한 규칙.
 *
 * 공개 사이트는 로그인 없이 읽을 수 있어야 하고, 쓰기는 관리자만,
 * 그리고 관리자라도 CSRF 토큰이 있어야 합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String POST_JSON =
            "{\"categoryId\":\"artwork\",\"contentType\":\"PHOTO\",\"title\":\"테스트\"}";

    // ── 공개 읽기 ────────────────────────────────────────────────

    @Test
    @DisplayName("공개 사이트가 쓰는 조회 API 는 로그인 없이 열린다")
    void publicReadsAreOpen() throws Exception {
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/settings")).andExpect(status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    // ── 인증 ────────────────────────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 쓰기를 거부한다")
    void writesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/posts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(POST_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 페이지는 로그인하지 않으면 로그인 화면으로 보낸다")
    void adminPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login.html"));
    }

    @Test
    @DisplayName("관리자 화면의 비동기 요청에는 브라우저 팝업 대신 JSON 으로 답한다")
    void xhrGetsJsonInsteadOfBasicChallenge() throws Exception {
        mockMvc.perform(post("/api/settings").with(csrf())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ── CSRF ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("로그인했어도 CSRF 토큰이 없으면 쓰기를 거부한다")
    void writesRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON).content(POST_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("삭제도 CSRF 토큰을 요구한다")
    void deletesRequireCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("토큰과 권한이 모두 있으면 통과한다")
    void authenticatedWriteWithTokenSucceeds() throws Exception {
        mockMvc.perform(post("/api/posts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(POST_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("화면이 읽어 갈 수 있도록 CSRF 토큰을 쿠키로 내려준다")
    void csrfTokenIsExposedAsCookie() throws Exception {
        mockMvc.perform(get("/login.html"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    // ── 입력 검증 ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("제목이 없으면 왜 거부됐는지 알려 준다")
    void validationMessageIsReturned() throws Exception {
        mockMvc.perform(post("/api/posts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"artwork\",\"contentType\":\"PHOTO\",\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("제목")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("기본 카테고리는 지울 수 없다")
    void defaultCategoryCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/api/categories/cv").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("삭제할 수 없습니다")));
    }

    @Test
    @DisplayName("없는 파일은 404 로 답한다")
    void missingFileReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/files/does-not-exist.jpg"))
                .andExpect(status().isNotFound());
    }
}
