package com.portfolio.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 한 칼럼이 두 가지 의미로 쓰이던 예전 데이터를 새 칼럼으로 옮깁니다.
 *
 * 무엇을 옮기나
 *   posts.description  → ARTICLE 글의 본문을 posts.article_content 로
 *   post_images.image_url → 종류에 따라 file_name 또는 video_url 로
 *
 * 이미 옮긴 행은 건드리지 않으므로 여러 번 실행해도 안전합니다.
 * 운영 DB 가 모두 넘어온 뒤에는 이 클래스와 남은 예전 칼럼을 지워도 됩니다.
 *
 * 스키마 변경을 코드로 다루는 것은 임시 방편입니다. 변경이 더 늘어나면
 * Flyway 같은 마이그레이션 도구로 옮기는 편이 낫습니다.
 */
@Component
@Order(1)   // DataLoader 가 기본 데이터를 만들기 전에 기존 데이터를 정리합니다
@RequiredArgsConstructor
public class LegacyDataMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataMigration.class);

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        migrateArticleContent();
        migrateMediaColumns();
    }

    /** ARTICLE 본문을 description 에서 article_content 로 옮깁니다. */
    private void migrateArticleContent() {
        if (!columnExists("posts", "article_content")) {
            return;
        }
        int moved = jdbcTemplate.update(
                "UPDATE posts SET article_content = description, description = NULL " +
                "WHERE content_type = 'ARTICLE' AND article_content IS NULL AND description IS NOT NULL");
        if (moved > 0) {
            log.info("ARTICLE 본문 {}건을 article_content 로 옮겼습니다.", moved);
        }
    }

    /**
     * image_url 하나에 섞여 있던 값을 종류에 맞는 칼럼으로 나눕니다.
     * image_url 칼럼은 더 이상 매핑하지 않지만, 되돌릴 수 있도록 지우지는 않습니다.
     */
    private void migrateMediaColumns() {
        if (!columnExists("post_images", "image_url")
                || !columnExists("post_images", "file_name")
                || !columnExists("post_images", "video_url")) {
            return;
        }

        int videos = jdbcTemplate.update(
                "UPDATE post_images SET video_url = image_url " +
                "WHERE media_type = 'VIDEO' AND video_url IS NULL AND image_url IS NOT NULL");

        int images = jdbcTemplate.update(
                "UPDATE post_images SET file_name = image_url " +
                "WHERE (media_type IS NULL OR media_type <> 'VIDEO') " +
                "AND file_name IS NULL AND image_url IS NOT NULL");

        // 예전에는 media_type 이 비어 있으면 이미지로 취급했습니다.
        int typed = jdbcTemplate.update(
                "UPDATE post_images SET media_type = 'IMAGE' WHERE media_type IS NULL");

        if (videos + images + typed > 0) {
            log.info("미디어 데이터를 옮겼습니다. 영상 {}건, 이미지 {}건, 종류 보정 {}건",
                    videos, images, typed);
        }
    }

    /**
     * 칼럼이 있는지 확인합니다.
     *
     * 새로 만든 DB 에는 예전 칼럼(image_url)이 아예 없고, 운영 DB 에는 있습니다.
     * 두 경우 모두에서 같은 코드가 동작해야 하므로 먼저 확인합니다.
     */
    private boolean columnExists(String table, String column) {
        try {
            List<String> found = jdbcTemplate.query(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE UPPER(table_name) = UPPER(?) AND UPPER(column_name) = UPPER(?)",
                    (rs, rowNum) -> rs.getString(1),
                    table, column);
            return !found.isEmpty();
        } catch (Exception e) {
            log.warn("칼럼 확인에 실패해 마이그레이션을 건너뜁니다: {}.{} ({})", table, column, e.getMessage());
            return false;
        }
    }
}
