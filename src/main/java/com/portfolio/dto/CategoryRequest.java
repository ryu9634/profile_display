package com.portfolio.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

/**
 * 카테고리 생성·수정 요청 값입니다.
 *
 * id 는 주소(#/카테고리ID)에 그대로 쓰이므로 소문자·숫자·하이픈으로 제한합니다.
 */
@Data
public class CategoryRequest {

    @NotBlank(message = "카테고리 ID는 필수입니다")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "카테고리 ID는 소문자, 숫자, 하이픈만 사용 가능합니다")
    private String id;

    @NotBlank(message = "카테고리 이름은 필수입니다")
    private String name;

    @NotBlank(message = "타입은 필수입니다")
    @Pattern(regexp = "^(PHOTO|ARTICLE|HTML)$", message = "타입은 PHOTO, ARTICLE, HTML 중 하나여야 합니다")
    private String type = "PHOTO";

    private Boolean isDeletable = true;
}
