package com.portfolio.repository;

import com.portfolio.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByType(String type);
    List<Category> findByIsDeletable(Boolean isDeletable);
    List<Category> findAllByOrderByDisplayOrderAscCreatedAtAsc();
}
