package com.portfolio.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한 칼럼이 두 의미로 쓰이던 예전 데이터를 새 칼럼으로 옮기는 동작.
 *
 * 운영 DB 에는 예전 구조로 쌓인 데이터가 있으므로, 옮기는 것뿐 아니라
 * 여러 번 실행해도 망가지지 않는지가 중요합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LegacyDataMigrationTest {

    @Autowired private LegacyDataMigration migration;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM post_images");
        jdbc.execute("DELETE FROM posts");
        // 예전 스키마에만 있던 칼럼을 되살려 운영 DB 와 같은 상태를 만듭니다.
        if (!hasColumn("post_images", "image_url")) {
            jdbc.execute("ALTER TABLE post_images ADD COLUMN image_url VARCHAR(255)");
        }
    }

    private boolean hasColumn(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void insertPost(long id, String contentType, String description, String articleContent) {
        jdbc.update("INSERT INTO posts (id, category_id, content_type, title, post_year, medium, size, " +
                        "description, article_content, display_order, version) " +
                        "VALUES (?, 'artwork', ?, '제목', '2024', '재료', '크기', ?, ?, 0, 0)",
                id, contentType, description, articleContent);
    }

    private void insertMedia(long postId, String mediaType, String imageUrl) {
        jdbc.update("INSERT INTO post_images (post_id, media_type, image_url, display_order) VALUES (?, ?, ?, 0)",
                postId, mediaType, imageUrl);
    }

    @Test
    @DisplayName("ARTICLE 본문을 description 에서 article_content 로 옮긴다")
    void movesArticleBody() {
        insertPost(1L, "ARTICLE", "{\"ops\":[{\"insert\":\"본문\"}]}", null);

        migration.run();

        assertThat(jdbc.queryForObject("SELECT article_content FROM posts WHERE id = 1", String.class))
                .contains("본문");
        assertThat(jdbc.queryForObject("SELECT description FROM posts WHERE id = 1", String.class))
                .isNull();
    }

    @Test
    @DisplayName("PHOTO 의 평문 설명은 그대로 둔다")
    void leavesPhotoDescriptionAlone() {
        insertPost(2L, "PHOTO", "작품에 대한 설명", null);

        migration.run();

        assertThat(jdbc.queryForObject("SELECT description FROM posts WHERE id = 2", String.class))
                .isEqualTo("작품에 대한 설명");
        assertThat(jdbc.queryForObject("SELECT article_content FROM posts WHERE id = 2", String.class))
                .isNull();
    }

    @Test
    @DisplayName("이미 옮긴 ARTICLE 은 덮어쓰지 않는다")
    void doesNotOverwriteMigratedArticle() {
        insertPost(3L, "ARTICLE", "옛날 값", "이미 옮긴 본문");

        migration.run();

        assertThat(jdbc.queryForObject("SELECT article_content FROM posts WHERE id = 3", String.class))
                .isEqualTo("이미 옮긴 본문");
    }

    @Test
    @DisplayName("영상은 video_url 로, 이미지는 file_name 으로 나눈다")
    void splitsMediaByType() {
        insertPost(4L, "PHOTO", null, null);
        insertMedia(4L, "VIDEO", "https://youtu.be/abcdefghijk");
        insertMedia(4L, "IMAGE", "photo.jpg");

        migration.run();

        assertThat(jdbc.queryForObject(
                "SELECT video_url FROM post_images WHERE media_type = 'VIDEO'", String.class))
                .isEqualTo("https://youtu.be/abcdefghijk");
        assertThat(jdbc.queryForObject(
                "SELECT file_name FROM post_images WHERE media_type = 'VIDEO'", String.class))
                .isNull();

        assertThat(jdbc.queryForObject(
                "SELECT file_name FROM post_images WHERE media_type = 'IMAGE'", String.class))
                .isEqualTo("photo.jpg");
        assertThat(jdbc.queryForObject(
                "SELECT video_url FROM post_images WHERE media_type = 'IMAGE'", String.class))
                .isNull();
    }

    @Test
    @DisplayName("종류가 비어 있던 예전 행은 이미지로 본다")
    void treatsMissingTypeAsImage() {
        insertPost(5L, "PHOTO", null, null);
        insertMedia(5L, null, "legacy.jpg");

        migration.run();

        assertThat(jdbc.queryForObject("SELECT media_type FROM post_images", String.class))
                .isEqualTo("IMAGE");
        assertThat(jdbc.queryForObject("SELECT file_name FROM post_images", String.class))
                .isEqualTo("legacy.jpg");
    }

    @Test
    @DisplayName("여러 번 실행해도 결과가 같다")
    void isIdempotent() {
        insertPost(6L, "ARTICLE", "{\"ops\":[]}", null);
        insertMedia(6L, "IMAGE", "photo.jpg");

        migration.run();
        String articleAfterFirst =
                jdbc.queryForObject("SELECT article_content FROM posts WHERE id = 6", String.class);

        migration.run();
        migration.run();

        assertThat(jdbc.queryForObject("SELECT article_content FROM posts WHERE id = 6", String.class))
                .isEqualTo(articleAfterFirst);
        assertThat(jdbc.queryForObject("SELECT file_name FROM post_images", String.class))
                .isEqualTo("photo.jpg");
    }
}
