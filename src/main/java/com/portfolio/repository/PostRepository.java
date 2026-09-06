package com.portfolio.repository;

import com.portfolio.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByCategoryId(String categoryId);
    List<Post> findByCategoryIdOrderByDisplayOrderAscIdAsc(String categoryId);
    List<Post> findAllByOrderByDisplayOrderAscIdAsc();
    Optional<Post> findTopByCategoryIdOrderByDisplayOrderDesc(String categoryId);
    long countByCategoryId(String categoryId);
    void deleteByCategoryId(String categoryId);
}
