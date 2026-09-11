package com.portfolio.repository;

import com.portfolio.model.SiteSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 사이트 설정 저장소. 설정은 항상 id=1 인 한 건만 존재합니다.
 */
@Repository
public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Long> {
}
