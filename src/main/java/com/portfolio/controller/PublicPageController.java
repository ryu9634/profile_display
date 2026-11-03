package com.portfolio.controller;

import com.portfolio.model.Category;
import com.portfolio.model.Post;
import com.portfolio.model.SiteSettings;
import com.portfolio.repository.CategoryRepository;
import com.portfolio.repository.PostRepository;
import com.portfolio.service.PostService;
import com.portfolio.service.SeoService;
import com.portfolio.service.SiteSettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 공개 사이트의 화면 주소를 처리합니다.
 *
 * <pre>
 *   /                 메인
 *   /artwork          카테고리 목록
 *   /artwork/12       작품 상세
 * </pre>
 *
 * 세 주소 모두 같은 index.html 을 내려주고, 화면 구성은 자바스크립트가 합니다.
 * 서버가 하는 일은 하나뿐입니다 — {@code <head>} 의 메타 태그를 그 화면에 맞게
 * 바꿔 끼우는 것. 크롤러와 메신저 미리보기 봇은 스크립트를 실행하지 않으므로,
 * 이 작업이 없으면 어떤 작품을 공유해도 사이트 대표 문구만 보입니다.
 *
 * <h3>경로 패턴에 {@code [^.]+} 를 쓴 이유</h3>
 * {@code /{categoryId}} 는 Spring MVC 에서 정적 리소스 핸들러보다 먼저 평가됩니다.
 * 제한을 두지 않으면 {@code /favicon.ico} 나 {@code /robots.txt} 까지 이 메서드가
 * 가로채 HTML 을 돌려줍니다. 점이 없는 한 조각만 받도록 해서 파일 요청과 구분합니다.
 * ({@code /admin}, {@code /login} 처럼 정확히 일치하는 매핑은 패턴보다 우선합니다)
 */
@Controller
@RequiredArgsConstructor
public class PublicPageController {

    private static final Logger log = LoggerFactory.getLogger(PublicPageController.class);

    /** index.html 안의 이 구간을 화면별 메타 태그로 교체합니다. */
    private static final String MARKER_START = "<!--SEO:start-->";
    private static final String MARKER_END = "<!--SEO:end-->";

    private static final String INDEX_HTML = "static/index.html";

    // Content-Type 에 문자셋을 명시합니다. 비워 두면 String 을 내보내는 변환기가
    // ISO-8859-1 로 쓰기 때문에 한글 제목과 설명이 깨집니다.
    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final MediaType XML_UTF8 = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);
    private static final MediaType TEXT_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final SeoService seoService;
    private final SiteSettingsService siteSettingsService;
    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    /** 리버스 프록시가 X-Forwarded-* 를 보내지 않는 환경에서 직접 지정할 수 있게 열어 둡니다. */
    @Value("${site.base-url:}")
    private String configuredBaseUrl;

    /** JAR 안의 파일은 실행 중에 바뀌지 않으므로 한 번만 읽습니다. */
    private volatile String cachedTemplate;

    @GetMapping({"/", "/{categoryId:[^.]+}", "/{categoryId:[^.]+}/{postId:\\d+}"})
    public ResponseEntity<String> page(@PathVariable(required = false) String categoryId,
                                       @PathVariable(required = false) Long postId) {
        SiteSettings settings = siteSettingsService.getSettings();

        // 없는 카테고리·작품이어도 404 대신 화면을 내려줍니다.
        // 자바스크립트가 메인으로 되돌리므로, 주소를 잘못 입력한 방문자에게
        // 흰 화면보다 사이트를 보여주는 편이 낫습니다.
        Category category = categoryId == null ? null
                : categoryRepository.findById(categoryId).orElse(null);
        Post post = findPost(category, postId);

        String baseUrl = resolveBaseUrl();
        String canonicalUrl = seoService.canonicalUrl(baseUrl, category, post);
        String head = seoService.headTags(settings, category, post, baseUrl, canonicalUrl);

        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                // 관리자가 설정이나 작품을 바꾸면 바로 반영돼야 하므로 캐시하지 않습니다.
                // (스타일·스크립트 같은 정적 파일은 WebConfig 에서 따로 캐시합니다)
                .cacheControl(CacheControl.noCache())
                .body(render(head));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String baseUrl = resolveBaseUrl();
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc();
        List<Post> posts = postService.getAllPosts();

        return ResponseEntity.ok()
                .contentType(XML_UTF8)
                .cacheControl(CacheControl.noCache())
                .body(seoService.sitemapXml(siteSettingsService.getSettings(), categories, posts, baseUrl));
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok()
                .contentType(TEXT_UTF8)
                .body(seoService.robotsTxt(resolveBaseUrl()));
    }

    // ============================
    // 내부 처리
    // ============================

    /**
     * 작품을 찾습니다. 주소의 카테고리와 실제 소속이 다르면 없는 것으로 봅니다.
     * 같은 작품이 여러 주소로 색인되면 검색 엔진이 중복으로 처리하기 때문입니다.
     */
    private Post findPost(Category category, Long postId) {
        if (category == null || postId == null) {
            return null;
        }
        return postRepository.findById(postId)
                .filter(p -> category.getId().equals(p.getCategoryId()))
                .orElse(null);
    }

    /**
     * 요청에서 "https://example.com" 형태를 만듭니다.
     *
     * 프록시 뒤에서는 요청 자체가 http 로 보이므로, prod 프로파일의
     * {@code server.forward-headers-strategy=native} 가 X-Forwarded-Proto 를
     * 반영해 줍니다. 그래도 맞지 않는 환경이라면 site.base-url 로 못박습니다.
     */
    private String resolveBaseUrl() {
        if (configuredBaseUrl != null && !configuredBaseUrl.trim().isEmpty()) {
            return trimTrailingSlash(configuredBaseUrl.trim());
        }
        return trimTrailingSlash(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String render(String headTags) {
        String template = template();
        int start = template.indexOf(MARKER_START);
        int end = template.indexOf(MARKER_END);
        if (start < 0 || end < start) {
            // 마커가 사라졌다면 메타 태그만 포기하고 화면은 그대로 내려줍니다.
            log.warn("index.html 에서 {} 마커를 찾지 못했습니다. 메타 태그 없이 응답합니다.", MARKER_START);
            return template;
        }
        return template.substring(0, start) + headTags + template.substring(end + MARKER_END.length());
    }

    private String template() {
        String cached = cachedTemplate;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedTemplate == null) {
                cachedTemplate = readIndexHtml();
            }
            return cachedTemplate;
        }
    }

    private String readIndexHtml() {
        ClassPathResource resource = new ClassPathResource(INDEX_HTML);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new IllegalStateException(INDEX_HTML + " 을 읽을 수 없습니다", e);
        }
    }
}
