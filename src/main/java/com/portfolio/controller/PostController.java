package com.portfolio.controller;

import com.portfolio.dto.PostRequest;
import com.portfolio.dto.ReorderRequest;
import com.portfolio.model.Post;
import com.portfolio.service.PostService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시글(작품) CRUD API.
 *
 * 조회는 누구나, 생성/수정/삭제와 순서 변경은 관리자만 호출할 수 있습니다.
 * (권한 분리는 {@code SecurityConfig} 에서 경로와 HTTP 메서드로 처리합니다)
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<List<Post>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Post>> getPostsByCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(postService.getPostsByCategory(categoryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Post> getPostById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @PostMapping
    public ResponseEntity<Post> createPost(@Valid @RequestBody PostRequest request) {
        Post post = postService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        postService.reorderPosts(request.getIds());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Post> updatePost(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        Post post = postService.updatePost(id, request);
        return ResponseEntity.ok(post);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }
}
