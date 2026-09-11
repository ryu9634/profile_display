package com.portfolio.dto;

import lombok.Data;

/**
 * 사이트 설정 저장 요청 값입니다. 관리자 화면의 입력 항목과 1:1로 대응합니다.
 */
@Data
public class SiteSettingsRequest {
    private String siteTitle;
    private String siteSubtitle;
    private String backgroundColor;
    private String backgroundImage;
    private String backgroundSize;
    private String backgroundRepeat;
    private String backgroundAttachment;
    private String backgroundPosition;
    private String textColor;
    private String sidebarBgColor;
    private String sidebarTextColor;
    private String sidebarActiveColor;
    private String titleFontUrl;
    private String titleFontFamily;
    private String bodyFontUrl;
    private String bodyFontFamily;
    private String logoImage;
    private String favicon;
    private String contactName;
    private String contactPhone;
    private String socialInstagram;
    private String socialBehance;
    private String socialEmail;
    private String socialWebsite;
    private String socialLinkedin;
    private String footerText;
    private String mainIntroText;
    private Boolean showRecentOnMain;
}
