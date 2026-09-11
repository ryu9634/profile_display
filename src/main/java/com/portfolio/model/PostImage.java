package com.portfolio.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 게시글에 딸린 이미지 또는 영상 한 개입니다.
 *
 * 별도 엔티티가 아니라 Embeddable 인 이유는 게시글과 생명주기가 같고
 * 단독으로 조회할 일이 없기 때문입니다.
 *
 * mediaType 이 VIDEO 면 imageUrl 에 업로드 파일명이 아니라 YouTube 주소가 들어갑니다.
 * showDescription, showImageDescription, descriptionPosition 은 상세 화면에서
 * 설명을 어디에 보여줄지 정하는 표시 옵션입니다.
 */
@Embeddable
@Data
@NoArgsConstructor
public class PostImage {

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_description", columnDefinition = "TEXT")
    private String imageDescription;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "media_type")
    private String mediaType = "IMAGE";

    @Column(name = "show_description")
    private Boolean showDescription = true;

    @Column(name = "show_image_description")
    private Boolean showImageDescription = false;

    @Column(name = "description_position")
    private String descriptionPosition = "right";
}
