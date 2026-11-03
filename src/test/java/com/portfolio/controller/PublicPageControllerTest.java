package com.portfolio.controller;

import com.portfolio.model.Category;
import com.portfolio.model.ContentType;
import com.portfolio.model.Post;
import com.portfolio.repository.CategoryRepository;
import com.portfolio.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 작품 주소로 직접 들어왔을 때 서버가 무엇을 내려주는지.
 *
 * 해시 라우팅에서는 서버가 주소를 알 수 없어 어떤 작품을 공유해도 같은
 * 미리보기가 나왔습니다. 이 테스트는 그 상태로 되돌아가지 않는지를 봅니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicPageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostRepository postRepository;

    private Long postId;
    private String categoryName;

    @BeforeEach
    void setUp() {
        // 초기 데이터(DataLoader)가 이미 만들어 둔 카테고리가 있으면 그대로 씁니다.
        Category category = categoryRepository.findById("artwork").orElseGet(() -> {
            Category created = new Category();
            created.setId("artwork");
            created.setName("Artwork");
            created.setType("PHOTO");
            return categoryRepository.save(created);
        });
        categoryName = category.getName();

        Post post = new Post();
        post.setCategoryId("artwork");
        post.setContentType(ContentType.PHOTO);
        post.setTitle("붉은 방");
        post.setYear("2024");
        post.setMedium("캔버스에 유채");
        post.setSize("100x80cm");
        post.setDescription("공유 링크에 이 설명이 보여야 합니다.");
        post.setThumbnail("abc.jpg");
        postId = postRepository.save(post).getId();
    }

    @Test
    @DisplayName("작품 주소로 들어오면 그 작품의 메타 태그가 담긴 HTML 이 나온다")
    void postPathServesPostMeta() throws Exception {
        String html = mockMvc.perform(get("/artwork/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<title>붉은 방 ·");
        assertThat(html).contains("공유 링크에 이 설명이 보여야 합니다.");
        assertThat(html).contains("og:image");
        assertThat(html).contains("/artwork/" + postId);

        // 화면은 그대로 자바스크립트가 그립니다 — 본문은 index.html 과 같아야 합니다.
        assertThat(html).contains("id=\"page-content\"");
        assertThat(html).contains("/scripts/main.js");

        // 교체가 일어났으므로 마커와 기본 문구는 남아 있지 않습니다.
        assertThat(html).doesNotContain("<!--SEO:start-->");
        assertThat(html).doesNotContain("Artist portfolio - works, exhibitions and CV.");
    }

    @Test
    @DisplayName("카테고리 주소와 메인은 각자의 제목을 쓴다")
    void categoryAndMainHaveOwnTitles() throws Exception {
        String category = body("/artwork");
        assertThat(category).contains("<title>" + categoryName + " ·");

        String main = body("/");
        assertThat(main).contains("<title>");
        assertThat(main).doesNotContain("<title>" + categoryName + " ·");
    }

    @Test
    @DisplayName("없는 작품·카테고리도 화면은 내려준다 (자바스크립트가 메인으로 되돌림)")
    void unknownPathsStillServeThePage() throws Exception {
        assertThat(body("/artwork/999999")).contains("id=\"page-content\"");
        assertThat(body("/nonexistent")).contains("id=\"page-content\"");
    }

    @Test
    @DisplayName("다른 카테고리 주소로 부른 작품은 그 작품으로 취급하지 않는다")
    void postMustBelongToCategoryInPath() throws Exception {
        Category other = new Category();
        other.setId("news");
        other.setName("News");
        other.setType("ARTICLE");
        categoryRepository.save(other);

        // 같은 작품이 /artwork/12 와 /news/12 양쪽으로 색인되면 중복이 됩니다.
        assertThat(body("/news/" + postId)).doesNotContain("<title>붉은 방 ·");
    }

    @Test
    @DisplayName("정적 파일 요청을 화면 라우팅이 가로채지 않는다")
    void staticFilesAreNotHijacked() throws Exception {
        mockMvc.perform(get("/styles/main.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mockMvc.perform(get("/scripts/main.js"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sitemap.xml 에 작품 주소가 들어간다")
    void sitemapListsPosts() throws Exception {
        String xml = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn().getResponse().getContentAsString();

        assertThat(xml).contains("<loc>");
        assertThat(xml).contains("/artwork/" + postId + "</loc>");
    }

    @Test
    @DisplayName("robots.txt 는 관리자 색인을 막고 사이트맵 위치를 알려준다")
    void robotsIsServed() throws Exception {
        String robots = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(robots).contains("Disallow: /admin");
        assertThat(robots).contains("sitemap.xml");
    }

    private String body(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
