package com.portfolio.repository;

import com.portfolio.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 카테고리 조회. 사이드바 표시 순서(displayOrder)를 따르고,
 * 값이 같으면 만들어진 순서로 정렬합니다.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByType(String type);
    List<Category> findByIsDeletable(Boolean isDeletable);
    List<Category> findAllByOrderByDisplayOrderAscCreatedAtAsc();
}
