package com.portfolio.model;

/**
 * 게시글의 콘텐츠 형식.
 *
 * 형식에 따라 쓰는 필드가 다릅니다.
 *   PHOTO   - 이미지 갤러리. {@code description} 에 평문 설명을 둡니다.
 *   ARTICLE - 리치 텍스트 글. {@code articleContent} 에 Quill 델타를 JSON 으로 둡니다.
 *   HTML    - 직접 작성한 HTML. {@code htmlContent} 를 씁니다.
 *
 * DB 에는 이름 그대로(PHOTO/ARTICLE/HTML) 저장합니다. 순서가 바뀌어도
 * 기존 데이터가 깨지지 않도록 EnumType.ORDINAL 이 아니라 STRING 을 씁니다.
 */
public enum ContentType {
    PHOTO,
    ARTICLE,
    HTML
}
