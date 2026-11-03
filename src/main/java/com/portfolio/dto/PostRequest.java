package com.portfolio.dto;

import com.portfolio.model.ContentType;
import com.portfolio.model.PostImage;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 게시글 생성·수정 요청 값입니다.
 *
 * 연도·재료·크기는 작품에 따라 비어 있을 수 있어 필수로 두지 않았고,
 * 목록에 빈 값이 그대로 노출되지 않도록 표시할 때 걸러 냅니다.
 */
@Data
public class PostRequest {

    @NotBlank(message = "카테고리 ID는 필수입니다")
    private String categoryId;

    @NotNull(message = "콘텐츠 타입은 필수입니다")
    private ContentType contentType;

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    private String year;

    private String medium;

    private String size;

    private String thumbnail;

    /** PHOTO 의 평문 설명 */
    private String description;

    /** ARTICLE 본문 (Quill 델타 JSON) */
    private String articleContent;

    private String videoUrl;

    private String htmlContent;

    private List<PostImage> images = new ArrayList<>();

    private Integer displayOrder;
}
