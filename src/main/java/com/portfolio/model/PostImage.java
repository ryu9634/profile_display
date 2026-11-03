package com.portfolio.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 게시글에 딸린 이미지 또는 영상 한 개입니다.
 *
 * 별도 엔티티가 아니라 Embeddable 인 이유는 게시글과 생명주기가 같고
 * 단독으로 조회할 일이 없기 때문입니다.
 *
 * 종류에 따라 쓰는 필드가 다릅니다.
 *   IMAGE - {@link #fileName} 에 업로드된 파일명
 *   VIDEO - {@link #videoUrl} 에 YouTube 주소
 *
 * 예전에는 imageUrl 하나가 종류에 따라 파일명이거나 YouTube 주소였습니다.
 * 읽는 쪽이 mediaType 을 먼저 보고 의미를 결정해야 했고, 영상 주소를
 * 파일명으로 착각해 삭제를 시도하는 실수가 생기기 쉬웠습니다.
 *
 * showDescription, showImageDescription, descriptionPosition 은
 * 상세 화면에서 설명을 어디에 보여줄지 정하는 표시 옵션입니다.
 */
@Embeddable
@Data
@NoArgsConstructor
public class PostImage {

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 10)
    private MediaType mediaType = MediaType.IMAGE;

    /** IMAGE 일 때 업로드된 파일명 */
    @Column(name = "file_name")
    private String fileName;

    /** VIDEO 일 때 YouTube 주소 */
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "image_description", columnDefinition = "TEXT")
    private String imageDescription;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "show_description")
    private Boolean showDescription = true;

    @Column(name = "show_image_description")
    private Boolean showImageDescription = false;

    @Column(name = "description_position")
    private String descriptionPosition = "right";

    /** 이 항목이 가리키는 업로드 파일명. 영상이면 파일이 없으므로 null 입니다. */
    public String uploadedFileName() {
        return mediaType == MediaType.VIDEO ? null : fileName;
    }
}
