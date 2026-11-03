package com.portfolio.model;

/**
 * 게시글에 붙는 미디어의 종류.
 *
 * 종류에 따라 쓰는 필드가 다릅니다.
 *   IMAGE - 업로드한 파일. {@code fileName} 을 씁니다.
 *   VIDEO - YouTube 영상. {@code videoUrl} 을 씁니다.
 */
public enum MediaType {
    IMAGE,
    VIDEO
}
