package com.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.Category;
import com.portfolio.model.Post;
import com.portfolio.model.SiteSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 링크 공유와 검색 노출을 위한 메타 태그 · 사이트맵 생성.
 *
 * 화면은 자바스크립트가 그리지만, 카카오톡·슬랙의 미리보기 봇과 검색 크롤러는
 * 스크립트를 실행하지 않고 첫 HTML 응답의 {@code <head>} 만 읽습니다.
 * 그래서 작품 하나하나의 제목 · 설명 · 대표 이미지를 서버가 직접 심어 줍니다.
 *
 * 이 클래스는 문자열만 만듭니다. 실제로 응답에 끼워 넣는 일은
 * {@code PublicPageController} 가 합니다.
 */
@Service
public class SeoService {

    private static final Logger log = LoggerFactory.getLogger(SeoService.class);

    /** 검색 결과 스니펫이 잘리는 길이에 맞췄습니다. 더 길게 넣어도 노출되지 않습니다. */
    private static final int DESCRIPTION_LIMIT = 160;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter SITEMAP_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /**
     * {@code <head>} 에 넣을 메타 태그 묶음.
     *
     * @param settings     사이트 설정 (제목, 소개 문구, 로고)
     * @param category     현재 카테고리. 메인 화면이면 null
     * @param post         현재 작품. 목록 화면이면 null
     * @param baseUrl      "https://example.com" 형태의 절대 주소 (og:image 는 절대 주소여야 합니다)
     * @param canonicalUrl 이 화면의 정규 주소
     */
    public String headTags(SiteSettings settings, Category category, Post post,
                           String baseUrl, String canonicalUrl) {
        String siteTitle = firstNonBlank(settings.getSiteTitle(), "Portfolio");

        String title;
        String description;
        String image;
        String type;

        if (post != null) {
            // 작품 상세: 공유된 링크가 그 작품을 보여줘야 합니다.
            title = post.getTitle() + " · " + siteTitle;
            description = firstNonBlank(postSummary(post), metaLine(post), siteDescription(settings));
            image = fileAbsoluteUrl(baseUrl, post.getThumbnail());
            type = "article";
        } else if (category != null) {
            title = category.getName() + " · " + siteTitle;
            description = siteDescription(settings);
            image = fileAbsoluteUrl(baseUrl, settings.getLogoImage());
            type = "website";
        } else {
            title = firstNonBlank(settings.getSiteSubtitle(), siteTitle);
            description = siteDescription(settings);
            image = fileAbsoluteUrl(baseUrl, settings.getLogoImage());
            type = "website";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<title>").append(escapeText(title)).append("</title>\n");
        appendMeta(sb, "name", "description", description);
        sb.append("    <link rel=\"canonical\" href=\"").append(escapeAttr(canonicalUrl)).append("\">\n");

        appendMeta(sb, "property", "og:site_name", siteTitle);
        appendMeta(sb, "property", "og:title", title);
        appendMeta(sb, "property", "og:description", description);
        appendMeta(sb, "property", "og:type", type);
        appendMeta(sb, "property", "og:url", canonicalUrl);
        appendMeta(sb, "property", "og:image", image);

        // 이미지가 없으면 큰 카드를 요청해도 빈 칸만 나오므로 작은 카드로 둡니다.
        appendMeta(sb, "name", "twitter:card", image == null ? "summary" : "summary_large_image");
        appendMeta(sb, "name", "twitter:title", title);
        appendMeta(sb, "name", "twitter:description", description);
        appendMeta(sb, "name", "twitter:image", image);

        return sb.toString().trim();
    }

    /**
     * 이 화면의 정규 주소.
     *
     * 같은 작품이 여러 주소로 색인되지 않도록 한 작품에는 하나의 주소만 부여합니다.
     */
    public String canonicalUrl(String baseUrl, Category category, Post post) {
        if (post != null) {
            return baseUrl + "/" + encodePathSegment(post.getCategoryId()) + "/" + post.getId();
        }
        if (category != null && !isMain(category.getId())) {
            return baseUrl + "/" + encodePathSegment(category.getId());
        }
        return baseUrl + "/";
    }

    /**
     * 검색 엔진용 사이트맵.
     *
     * 메인 · 카테고리 목록 · 작품 상세를 모두 넣습니다. 작품이 가장 깊은 주소이지만
     * 실제로 공유되고 검색되는 대상이므로 우선순위를 낮추지 않았습니다.
     */
    public String sitemapXml(SiteSettings settings, List<Category> categories, List<Post> posts, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        appendUrl(sb, baseUrl + "/", lastModified(settings.getUpdatedAt()), "1.0");

        for (Category category : categories) {
            if (isMain(category.getId())) {
                continue;
            }
            appendUrl(sb, baseUrl + "/" + encodePathSegment(category.getId()), null, "0.8");
        }

        for (Post post : posts) {
            if (isMain(post.getCategoryId())) {
                continue;
            }
            String url = baseUrl + "/" + encodePathSegment(post.getCategoryId()) + "/" + post.getId();
            appendUrl(sb, url, lastModified(post.getUpdatedAt()), "0.6");
        }

        sb.append("</urlset>\n");
        return sb.toString();
    }

    /**
     * 크롤러 안내. 관리자 화면과 API 는 색인할 이유가 없습니다.
     * (색인을 막는 것이지 접근을 막는 것은 아닙니다. 권한은 SecurityConfig 가 담당합니다)
     */
    public String robotsTxt(String baseUrl) {
        return "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /admin\n"
                + "Disallow: /login\n"
                + "Disallow: /api/\n"
                + "\n"
                + "Sitemap: " + baseUrl + "/sitemap.xml\n";
    }

    // ============================
    // 설명 문구 추출
    // ============================

    /**
     * 작품의 설명을 평문으로 뽑습니다.
     *
     * 콘텐츠 종류마다 본문이 담긴 칼럼이 다릅니다. 어느 쪽이든 메타 태그에는
     * 태그와 JSON 이 아닌 사람이 읽을 문장이 들어가야 합니다.
     */
    String postSummary(Post post) {
        String plain = post.getDescription();
        if (isNotBlank(plain)) {
            return truncate(collapseWhitespace(plain));
        }

        String fromDelta = quillDeltaToText(post.getArticleContent());
        if (isNotBlank(fromDelta)) {
            return truncate(collapseWhitespace(fromDelta));
        }

        String fromHtml = stripTags(post.getHtmlContent());
        if (isNotBlank(fromHtml)) {
            return truncate(collapseWhitespace(fromHtml));
        }

        return null;
    }

    /**
     * Quill 에디터가 저장한 델타 JSON 에서 글자만 모읍니다.
     *
     * 정규식으로 긁으면 이스케이프된 따옴표에서 깨지므로 제대로 파싱합니다.
     * 형식이 아니면(예전 평문 데이터) 조용히 포기합니다 — 메타 태그는
     * 없어도 화면이 동작하는 부가 정보이기 때문입니다.
     */
    String quillDeltaToText(String deltaJson) {
        if (!isNotBlank(deltaJson)) {
            return null;
        }
        try {
            JsonNode ops = MAPPER.readTree(deltaJson).path("ops");
            if (!ops.isArray()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode op : ops) {
                JsonNode insert = op.path("insert");
                if (insert.isTextual()) {
                    sb.append(insert.asText());
                }
                if (sb.length() > DESCRIPTION_LIMIT * 3) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("본문 JSON 파싱 실패 (메타 설명 생략): {}", e.getMessage());
            return null;
        }
    }

    /** 설명이 아예 없는 작품을 위한 대체 문구. "2024, 캔버스에 유채, 100x80cm" */
    String metaLine(Post post) {
        StringBuilder sb = new StringBuilder();
        for (String value : new String[]{post.getYear(), post.getMedium(), post.getSize()}) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty() || "-".equals(trimmed)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(trimmed);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String siteDescription(SiteSettings settings) {
        String intro = stripTags(settings.getMainIntroText());
        return firstNonBlank(
                isNotBlank(intro) ? truncate(collapseWhitespace(intro)) : null,
                settings.getSiteSubtitle(),
                settings.getSiteTitle());
    }

    // ============================
    // 문자열 유틸
    // ============================

    /**
     * 업로드 파일의 절대 주소. PDF 는 미리보기 이미지로 쓸 수 없어 제외합니다.
     *
     * 축소본(_thumb)이 더 가볍지만, 예전에 올린 파일에는 축소본이 없을 수 있습니다.
     * 미리보기 이미지가 404 면 카드 자체가 비어 버리므로 항상 있는 원본을 씁니다.
     */
    private String fileAbsoluteUrl(String baseUrl, String fileName) {
        if (!isNotBlank(fileName) || fileName.toLowerCase().endsWith(".pdf")) {
            return null;
        }
        return baseUrl + "/api/files/" + encodePathSegment(fileName);
    }

    private static void appendMeta(StringBuilder sb, String keyAttr, String key, String content) {
        if (!isNotBlank(content)) {
            return;
        }
        sb.append("    <meta ").append(keyAttr).append("=\"").append(key)
          .append("\" content=\"").append(escapeAttr(content)).append("\">\n");
    }

    private static void appendUrl(StringBuilder sb, String location, String lastModified, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeText(location)).append("</loc>\n");
        if (lastModified != null) {
            sb.append("    <lastmod>").append(lastModified).append("</lastmod>\n");
        }
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private static String lastModified(Long epochMillis) {
        return epochMillis == null ? null : SITEMAP_DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    private static boolean isMain(String categoryId) {
        return "main".equals(categoryId);
    }

    static String encodePathSegment(String value) {
        try {
            // URLEncoder 는 폼 인코딩용이라 공백을 '+' 로 바꿉니다. 경로에서는 %20 이어야 합니다.
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 을 지원하지 않는 환경입니다", e);
        }
    }

    private static String stripTags(String html) {
        if (!isNotBlank(html)) {
            return null;
        }
        return html.replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
    }

    private static String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value) {
        if (value.length() <= DESCRIPTION_LIMIT) {
            return value;
        }
        return value.substring(0, DESCRIPTION_LIMIT - 1).trim() + "…";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 속성값 이스케이프.
     *
     * 작품 제목과 설명은 관리자가 자유롭게 입력하는 값입니다. 따옴표 하나만 섞여도
     * 태그가 깨지고, 그 틈으로 스크립트를 넣을 수 있으므로 반드시 거쳐야 합니다.
     */
    static String escapeAttr(String value) {
        return escapeText(value).replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
