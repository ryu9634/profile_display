package com.portfolio.service;

import com.portfolio.model.Category;
import com.portfolio.model.ContentType;
import com.portfolio.model.Post;
import com.portfolio.model.SiteSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메타 태그와 사이트맵 생성.
 *
 * 메타 태그는 화면에 보이지 않기 때문에 눈으로 확인하기 어렵고, 잘못돼도
 * 공유해 보기 전까지 모릅니다. 그래서 문자열을 직접 검증합니다.
 */
class SeoServiceTest {

    private SeoService seoService;
    private SiteSettings settings;

    private static final String BASE = "https://example.com";

    @BeforeEach
    void setUp() {
        seoService = new SeoService();
        settings = new SiteSettings();
        settings.setSiteTitle("Jaehoon Jeong");
        settings.setSiteSubtitle("Jaehoon Jeong · Artist");
        settings.setMainIntroText("회화와 설치를 오가며 작업합니다.");
    }

    private Category category(String id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Post post(Long id, String categoryId, String title) {
        Post post = new Post();
        post.setId(id);
        post.setCategoryId(categoryId);
        post.setContentType(ContentType.PHOTO);
        post.setTitle(title);
        post.setYear("2024");
        post.setMedium("캔버스에 유채");
        post.setSize("100x80cm");
        return post;
    }

    // ============================
    // 작품 상세
    // ============================

    @Test
    @DisplayName("작품 주소에는 그 작품의 제목과 대표 이미지가 들어간다")
    void postPageUsesPostTitleAndImage() {
        Post post = post(12L, "artwork", "붉은 방");
        post.setDescription("2024년에 그린 연작의 첫 번째 작품입니다.");
        post.setThumbnail("abc.jpg");

        String head = seoService.headTags(settings, category("artwork", "Artwork"), post,
                BASE, BASE + "/artwork/12");

        assertThat(head).contains("<title>붉은 방 · Jaehoon Jeong</title>");
        assertThat(head).contains("<meta property=\"og:title\" content=\"붉은 방 · Jaehoon Jeong\">");
        assertThat(head).contains("content=\"2024년에 그린 연작의 첫 번째 작품입니다.\"");
        assertThat(head).contains("<meta property=\"og:image\" content=\"https://example.com/api/files/abc.jpg\">");
        assertThat(head).contains("<meta property=\"og:url\" content=\"https://example.com/artwork/12\">");
        assertThat(head).contains("<link rel=\"canonical\" href=\"https://example.com/artwork/12\">");
        assertThat(head).contains("<meta property=\"og:type\" content=\"article\">");
        assertThat(head).contains("content=\"summary_large_image\"");
    }

    @Test
    @DisplayName("설명이 없으면 연도·재료·크기를 설명으로 쓴다")
    void fallsBackToMetaLine() {
        Post post = post(3L, "artwork", "무제");

        String head = seoService.headTags(settings, category("artwork", "Artwork"), post,
                BASE, BASE + "/artwork/3");

        assertThat(head).contains("content=\"2024, 캔버스에 유채, 100x80cm\"");
    }

    @Test
    @DisplayName("ARTICLE 본문은 JSON 이 아니라 읽을 수 있는 문장으로 들어간다")
    void extractsTextFromQuillDelta() {
        Post post = post(5L, "news", "전시 소식");
        post.setContentType(ContentType.ARTICLE);
        post.setArticleContent("{\"ops\":[{\"insert\":\"3월 10일부터 \"},"
                + "{\"insert\":\"서울\",\"attributes\":{\"bold\":true}},"
                + "{\"insert\":\" 에서 전시를 엽니다.\\n\"}]}");

        String head = seoService.headTags(settings, category("news", "News"), post,
                BASE, BASE + "/news/5");

        assertThat(head).contains("content=\"3월 10일부터 서울 에서 전시를 엽니다.\"");
        assertThat(head).doesNotContain("\"ops\"");
    }

    @Test
    @DisplayName("예전 평문이 본문 칼럼에 남아 있어도 메타 태그를 만든다")
    void survivesNonJsonArticleContent() {
        Post post = post(6L, "news", "옛 글");
        post.setContentType(ContentType.ARTICLE);
        post.setArticleContent("JSON 이 아닌 평문입니다");

        String head = seoService.headTags(settings, category("news", "News"), post,
                BASE, BASE + "/news/6");

        // 본문을 읽지 못하면 연도·재료로 대체하고, 제목과 주소는 그대로 유지합니다.
        assertThat(head).contains("<title>옛 글 · Jaehoon Jeong</title>");
        assertThat(head).contains("<meta property=\"og:url\" content=\"https://example.com/news/6\">");
    }

    @Test
    @DisplayName("HTML 본문에서는 태그를 걷어낸다")
    void stripsHtmlTags() {
        Post post = post(7L, "about", "소개");
        post.setContentType(ContentType.HTML);
        post.setHtmlContent("<p>안녕하세요. <strong>작가</strong>입니다.</p>");

        String head = seoService.headTags(settings, category("about", "About"), post,
                BASE, BASE + "/about/7");

        assertThat(head).contains("content=\"안녕하세요. 작가 입니다.\"");
        assertThat(head).doesNotContain("<strong>");
    }

    @Test
    @DisplayName("긴 설명은 잘라낸다")
    void truncatesLongDescription() {
        Post post = post(8L, "artwork", "긴 설명");
        post.setDescription(repeat("가", 400));

        String head = seoService.headTags(settings, category("artwork", "Artwork"), post,
                BASE, BASE + "/artwork/8");

        assertThat(head).contains("…");
        assertThat(descriptionOf(head).length()).isLessThanOrEqualTo(160);
    }

    @Test
    @DisplayName("PDF 썸네일은 미리보기 이미지로 쓰지 않는다")
    void skipsPdfThumbnail() {
        Post post = post(9L, "cv", "CV");
        post.setThumbnail("resume.pdf");

        String head = seoService.headTags(settings, category("cv", "CV"), post,
                BASE, BASE + "/cv/9");

        assertThat(head).doesNotContain("og:image");
        assertThat(head).contains("content=\"summary\">");
    }

    @Test
    @DisplayName("제목에 섞인 따옴표가 태그를 깨뜨리지 않는다")
    void escapesQuotesInTitle() {
        Post post = post(10L, "artwork", "그는 \"떠났다\" <script>alert(1)</script>");

        String head = seoService.headTags(settings, category("artwork", "Artwork"), post,
                BASE, BASE + "/artwork/10");

        assertThat(head).doesNotContain("<script>");
        assertThat(head).contains("&quot;떠났다&quot;");
        assertThat(head).contains("&lt;script&gt;");
    }

    // ============================
    // 목록 · 메인
    // ============================

    @Test
    @DisplayName("카테고리 목록은 카테고리 이름을 제목으로 쓴다")
    void categoryPageUsesCategoryName() {
        String head = seoService.headTags(settings, category("artwork", "Artwork"), null,
                BASE, BASE + "/artwork");

        assertThat(head).contains("<title>Artwork · Jaehoon Jeong</title>");
        assertThat(head).contains("<meta property=\"og:type\" content=\"website\">");
        assertThat(head).contains("content=\"회화와 설치를 오가며 작업합니다.\"");
    }

    @Test
    @DisplayName("메인은 사이트 설정의 문구를 쓴다")
    void mainPageUsesSiteSettings() {
        String head = seoService.headTags(settings, null, null, BASE, BASE + "/");

        assertThat(head).contains("<title>Jaehoon Jeong · Artist</title>");
        assertThat(head).contains("<meta property=\"og:url\" content=\"https://example.com/\">");
    }

    @Test
    @DisplayName("설정이 비어 있어도 기본 제목으로 태그를 만든다")
    void worksWithEmptySettings() {
        String head = seoService.headTags(new SiteSettings(), null, null, BASE, BASE + "/");

        assertThat(head).contains("<title>Portfolio</title>");
        assertThat(head).contains("<link rel=\"canonical\"");
    }

    // ============================
    // 정규 주소
    // ============================

    @Test
    @DisplayName("정규 주소는 작품이 실제로 속한 카테고리를 쓴다")
    void canonicalUrlFollowsPostCategory() {
        Post post = post(12L, "artwork", "붉은 방");

        assertThat(seoService.canonicalUrl(BASE, category("news", "News"), post))
                .isEqualTo("https://example.com/artwork/12");
        assertThat(seoService.canonicalUrl(BASE, category("artwork", "Artwork"), null))
                .isEqualTo("https://example.com/artwork");
        assertThat(seoService.canonicalUrl(BASE, category("main", "Main"), null))
                .isEqualTo("https://example.com/");
        assertThat(seoService.canonicalUrl(BASE, null, null))
                .isEqualTo("https://example.com/");
    }

    @Test
    @DisplayName("한글 카테고리 ID 는 주소에 쓸 수 있게 인코딩한다")
    void encodesNonAsciiCategoryId() {
        assertThat(seoService.canonicalUrl(BASE, category("작품", "작품"), null))
                .isEqualTo("https://example.com/%EC%9E%91%ED%92%88");
    }

    // ============================
    // 사이트맵 · robots
    // ============================

    @Test
    @DisplayName("사이트맵에 메인·카테고리·작품이 모두 들어간다")
    void sitemapListsEveryPublicUrl() {
        Post post = post(12L, "artwork", "붉은 방");
        post.setUpdatedAt(1700000000000L);

        String xml = seoService.sitemapXml(settings,
                Arrays.asList(category("main", "Main"), category("artwork", "Artwork")),
                Collections.singletonList(post), BASE);

        assertThat(xml).contains("<loc>https://example.com/</loc>");
        assertThat(xml).contains("<loc>https://example.com/artwork</loc>");
        assertThat(xml).contains("<loc>https://example.com/artwork/12</loc>");
        assertThat(xml).contains("<lastmod>2023-11-14</lastmod>");
        // main 은 "/" 와 같은 화면이므로 중복으로 넣지 않습니다.
        assertThat(xml).doesNotContain("<loc>https://example.com/main</loc>");
    }

    @Test
    @DisplayName("robots.txt 는 관리자와 API 색인을 막고 사이트맵을 알려준다")
    void robotsPointsToSitemap() {
        String robots = seoService.robotsTxt(BASE);

        assertThat(robots).contains("Disallow: /admin");
        assertThat(robots).contains("Disallow: /api/");
        assertThat(robots).contains("Sitemap: https://example.com/sitemap.xml");
    }

    // ============================
    // 헬퍼
    // ============================

    private static String repeat(String value, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    /** name="description" 의 content 값만 꺼냅니다. */
    private static String descriptionOf(String head) {
        String marker = "<meta name=\"description\" content=\"";
        int start = head.indexOf(marker) + marker.length();
        return head.substring(start, head.indexOf("\">", start));
    }
}
