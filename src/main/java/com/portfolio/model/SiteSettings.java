package com.portfolio.model;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "site_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettings {

    @Id
    private Long id = 1L;

    // 사이트 정보
    @Column(name = "site_title")
    private String siteTitle;

    @Column(name = "site_subtitle")
    private String siteSubtitle;

    // 배경
    @Column(name = "background_color")
    private String backgroundColor = "#0a0a0a";

    @Column(name = "background_image")
    private String backgroundImage;

    @Column(name = "background_size")
    private String backgroundSize = "cover";

    @Column(name = "background_repeat")
    private String backgroundRepeat = "no-repeat";

    @Column(name = "background_attachment")
    private String backgroundAttachment = "fixed";

    @Column(name = "background_position")
    private String backgroundPosition = "center center";

    // 색상
    @Column(name = "text_color")
    private String textColor = "#ffffff";

    @Column(name = "sidebar_bg_color")
    private String sidebarBgColor = "#0a0a0a";

    @Column(name = "sidebar_text_color")
    private String sidebarTextColor = "#535353";

    @Column(name = "sidebar_active_color")
    private String sidebarActiveColor = "#ffffff";

    // 폰트
    @Column(name = "title_font_url", length = 500)
    private String titleFontUrl;

    @Column(name = "title_font_family")
    private String titleFontFamily;

    @Column(name = "body_font_url", length = 500)
    private String bodyFontUrl;

    @Column(name = "body_font_family")
    private String bodyFontFamily;

    // 로고
    @Column(name = "logo_image")
    private String logoImage;

    // 파비콘
    @Column(name = "favicon")
    private String favicon;

    // 연락처
    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    // 소셜 미디어
    @Column(name = "social_instagram")
    private String socialInstagram;

    @Column(name = "social_behance")
    private String socialBehance;

    @Column(name = "social_email")
    private String socialEmail;

    @Column(name = "social_website")
    private String socialWebsite;

    @Column(name = "social_linkedin")
    private String socialLinkedin;

    // 푸터
    @Column(name = "footer_text")
    private String footerText;

    // 메인 페이지 소개 문구
    @Column(name = "main_intro_text", columnDefinition = "TEXT")
    private String mainIntroText;

    // 메인 페이지에 최근 작품을 보여줄지 여부
    @Column(name = "show_recent_on_main")
    private Boolean showRecentOnMain = true;

    // 타임스탬프
    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = System.currentTimeMillis();
        updatedAt = System.currentTimeMillis();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = System.currentTimeMillis();
    }
}
