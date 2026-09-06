package com.portfolio.config;

import com.portfolio.model.Category;
import com.portfolio.repository.CategoryRepository;
import com.portfolio.service.AdminAccountService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final CategoryRepository categoryRepository;
    private final AdminAccountService adminAccountService;

    @Override
    public void run(String... args) throws Exception {
        // 관리자 계정 생성/초기화 (admin.reset-password 참고)
        adminAccountService.ensureAccount();

        if (categoryRepository.count() == 0) {
            categoryRepository.save(category("main", "Main", "PHOTO", false, 0));
            categoryRepository.save(category("artwork", "Art work", "PHOTO", true, 1));
            categoryRepository.save(category("cv", "CV", "PHOTO", false, 2));
            log.info("기본 카테고리가 생성되었습니다.");
            return;
        }

        // 이전 버전에서 만들어진 데이터 정리:
        // - 타입이 'default'인 카테고리는 관리자 폼에서 선택할 수 없어 수정이 막혔으므로 PHOTO로 변환
        // - 메인/CV 페이지는 삭제되면 사이트가 깨지므로 삭제 불가로 고정
        List<Category> all = categoryRepository.findAll();
        boolean changed = false;
        for (Category c : all) {
            if (c.getType() == null || "default".equalsIgnoreCase(c.getType())) {
                c.setType("PHOTO");
                changed = true;
            }
            if (("main".equals(c.getId()) || "cv".equals(c.getId())) && Boolean.TRUE.equals(c.getIsDeletable())) {
                c.setIsDeletable(false);
                changed = true;
            }
            if (c.getDisplayOrder() == null) {
                c.setDisplayOrder(0);
                changed = true;
            }
        }
        if (changed) {
            categoryRepository.saveAll(all);
            log.info("기존 카테고리 데이터를 정리했습니다.");
        }
    }

    private static Category category(String id, String name, String type, boolean deletable, int order) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setType(type);
        c.setIsDeletable(deletable);
        c.setDisplayOrder(order);
        return c;
    }
}
