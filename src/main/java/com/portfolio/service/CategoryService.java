package com.portfolio.service;

import com.portfolio.dto.CategoryRequest;
import com.portfolio.model.Category;
import com.portfolio.repository.CategoryRepository;
import com.portfolio.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc();
    }

    public Category getCategoryById(String id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다: " + id));
    }

    public long countPosts(String categoryId) {
        return postRepository.countByCategoryId(categoryId);
    }

    @Transactional
    public Category createCategory(CategoryRequest request) {
        if (categoryRepository.existsById(request.getId())) {
            throw new RuntimeException("이미 존재하는 카테고리 ID입니다: " + request.getId());
        }

        Category category = new Category();
        category.setId(request.getId());
        category.setName(request.getName().trim());
        category.setType(request.getType());
        category.setIsDeletable(request.getIsDeletable() == null || request.getIsDeletable());

        // 새 카테고리는 목록 맨 뒤에
        int next = categoryRepository.findAll().stream()
                .mapToInt(c -> c.getDisplayOrder() == null ? 0 : c.getDisplayOrder())
                .max().orElse(-1) + 1;
        category.setDisplayOrder(next);

        log.info("카테고리 생성: id={}, name={}", request.getId(), request.getName());
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(String id, CategoryRequest request) {
        Category category = getCategoryById(id);

        category.setName(request.getName().trim());
        category.setType(request.getType());

        log.info("카테고리 수정: id={}", id);
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(String id) {
        Category category = getCategoryById(id);

        if (!category.getIsDeletable()) {
            throw new RuntimeException("이 카테고리는 삭제할 수 없습니다");
        }

        int removed = postService.deletePostsInCategory(id);
        categoryRepository.deleteById(id);
        log.info("카테고리 삭제: id={}, 함께 삭제된 게시글={}개", id, removed);
    }

    @Transactional
    public void reorderCategories(List<String> ids) {
        int order = 0;
        for (String id : ids) {
            Category category = getCategoryById(id);
            category.setDisplayOrder(order++);
            categoryRepository.save(category);
        }
        log.info("카테고리 순서 변경: {}개", ids.size());
    }

    public List<Category> getCustomCategories() {
        return categoryRepository.findByIsDeletable(true);
    }
}
