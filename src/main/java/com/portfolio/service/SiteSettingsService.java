package com.portfolio.service;

import com.portfolio.dto.SiteSettingsRequest;
import com.portfolio.model.SiteSettings;
import com.portfolio.repository.SiteSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiteSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SiteSettingsService.class);
    private static final Long SETTINGS_ID = 1L;

    private final SiteSettingsRepository repository;
    private final FileStorageService fileStorageService;

    public SiteSettings getSettings() {
        return repository.findById(SETTINGS_ID)
                .orElseGet(() -> {
                    SiteSettings defaults = new SiteSettings();
                    defaults.setId(SETTINGS_ID);
                    return repository.save(defaults);
                });
    }

    @Transactional
    public SiteSettings updateSettings(SiteSettingsRequest request) {
        SiteSettings settings = getSettings();

        // 기존 배경 이미지 삭제
        if (settings.getBackgroundImage() != null
                && request.getBackgroundImage() != null
                && !settings.getBackgroundImage().equals(request.getBackgroundImage())) {
            fileStorageService.deleteFile(settings.getBackgroundImage());
        }
        if (settings.getBackgroundImage() != null && request.getBackgroundImage() == null) {
            fileStorageService.deleteFile(settings.getBackgroundImage());
        }

        // 기존 로고 이미지 삭제
        if (settings.getLogoImage() != null
                && request.getLogoImage() != null
                && !settings.getLogoImage().equals(request.getLogoImage())) {
            fileStorageService.deleteFile(settings.getLogoImage());
        }
        if (settings.getLogoImage() != null && request.getLogoImage() == null) {
            fileStorageService.deleteFile(settings.getLogoImage());
        }

        // 기존 파비콘 삭제
        if (settings.getFavicon() != null
                && request.getFavicon() != null
                && !settings.getFavicon().equals(request.getFavicon())) {
            fileStorageService.deleteFile(settings.getFavicon());
        }
        if (settings.getFavicon() != null && request.getFavicon() == null) {
            fileStorageService.deleteFile(settings.getFavicon());
        }

        settings.setSiteTitle(request.getSiteTitle());
        settings.setSiteSubtitle(request.getSiteSubtitle());
        settings.setBackgroundColor(request.getBackgroundColor());
        settings.setBackgroundImage(request.getBackgroundImage());
        settings.setBackgroundSize(request.getBackgroundSize());
        settings.setBackgroundRepeat(request.getBackgroundRepeat());
        settings.setBackgroundAttachment(request.getBackgroundAttachment());
        settings.setBackgroundPosition(request.getBackgroundPosition());
        settings.setTextColor(request.getTextColor());
        settings.setSidebarBgColor(request.getSidebarBgColor());
        settings.setSidebarTextColor(request.getSidebarTextColor());
        settings.setSidebarActiveColor(request.getSidebarActiveColor());
        settings.setTitleFontUrl(request.getTitleFontUrl());
        settings.setTitleFontFamily(request.getTitleFontFamily());
        settings.setBodyFontUrl(request.getBodyFontUrl());
        settings.setBodyFontFamily(request.getBodyFontFamily());
        settings.setLogoImage(request.getLogoImage());
        settings.setFavicon(request.getFavicon());
        settings.setContactName(request.getContactName());
        settings.setContactPhone(request.getContactPhone());
        settings.setSocialInstagram(request.getSocialInstagram());
        settings.setSocialBehance(request.getSocialBehance());
        settings.setSocialEmail(request.getSocialEmail());
        settings.setSocialWebsite(request.getSocialWebsite());
        settings.setSocialLinkedin(request.getSocialLinkedin());
        settings.setFooterText(request.getFooterText());
        settings.setMainIntroText(request.getMainIntroText());
        settings.setShowRecentOnMain(request.getShowRecentOnMain() == null || request.getShowRecentOnMain());

        log.info("사이트 설정 업데이트");
        return repository.save(settings);
    }
}
