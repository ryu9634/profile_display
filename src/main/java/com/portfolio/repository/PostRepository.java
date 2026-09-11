package com.portfolio.repository;

import com.portfolio.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 조회. 목록 정렬은 관리자가 지정한 displayOrder 를 먼저 따르고,
 * 값이 같으면 id 순으로 고정해 매번 순서가 뒤바뀌지 않게 합니다.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByCategoryId(String categoryId);
    List<Post> findByCategoryIdOrderByDisplayOrderAscIdAsc(String categoryId);
    List<Post> findAllByOrderByDisplayOrderAscIdAsc();
    Optional<Post> findTopByCategoryIdOrderByDisplayOrderDesc(String categoryId);
    long countByCategoryId(String categoryId);
    void deleteByCategoryId(String categoryId);
}
