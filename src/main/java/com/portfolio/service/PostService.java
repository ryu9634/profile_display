package com.portfolio.service;

import com.portfolio.dto.PostRequest;
import com.portfolio.model.Post;
import com.portfolio.model.PostImage;
import com.portfolio.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final FileStorageService fileStorageService;

    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    public List<Post> getPostsByCategory(String categoryId) {
        return postRepository.findByCategoryIdOrderByDisplayOrderAscIdAsc(categoryId);
    }

    public Post getPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다: " + id));
    }

    @Transactional
    public Post createPost(PostRequest request) {
        Post post = new Post();
        applyRequest(post, request);

        // 새 게시글은 해당 카테고리의 맨 뒤에 붙습니다 (관리자에서 순서 변경 가능)
        if (request.getDisplayOrder() != null) {
            post.setDisplayOrder(request.getDisplayOrder());
        } else {
            int next = postRepository.findTopByCategoryIdOrderByDisplayOrderDesc(request.getCategoryId())
                    .map(p -> (p.getDisplayOrder() == null ? 0 : p.getDisplayOrder()) + 1)
                    .orElse(0);
            post.setDisplayOrder(next);
        }

        log.info("게시글 생성: {}", request.getTitle());
        return postRepository.save(post);
    }

    @Transactional
    public Post updatePost(Long id, PostRequest request) {
        Post post = getPostById(id);

        // 새 요청에서 여전히 참조되는 파일 목록 (썸네일 + 이미지 미디어)
        Set<String> stillReferenced = new HashSet<>();
        if (request.getThumbnail() != null) {
            stillReferenced.add(request.getThumbnail());
        }
        if (request.getImages() != null) {
            for (PostImage img : request.getImages()) {
                if (img.getImageUrl() != null && !"VIDEO".equals(img.getMediaType())) {
                    stillReferenced.add(img.getImageUrl());
                }
            }
        }

        // 기존에 쓰이던 파일 목록
        Set<String> previouslyUsed = new HashSet<>();
        if (post.getThumbnail() != null) {
            previouslyUsed.add(post.getThumbnail());
        }
        if (post.getImages() != null) {
            for (PostImage img : post.getImages()) {
                if (img.getImageUrl() != null && !"VIDEO".equals(img.getMediaType())) {
                    previouslyUsed.add(img.getImageUrl());
                }
            }
        }

        // 더 이상 어디에서도 참조되지 않는 파일만 삭제
        // (썸네일이 미디어 목록 첫 항목으로 바뀌는 경우 등에서 파일이 사라지던 문제 수정)
        for (String old : previouslyUsed) {
            if (!stillReferenced.contains(old)) {
                fileStorageService.deleteFile(old);
            }
        }

        applyRequest(post, request);
        if (request.getDisplayOrder() != null) {
            post.setDisplayOrder(request.getDisplayOrder());
        }

        log.info("게시글 수정: id={}, title={}", id, request.getTitle());
        return postRepository.save(post);
    }

    @Transactional
    public void deletePost(Long id) {
        Post post = getPostById(id);
        deleteFilesOf(post);
        postRepository.deleteById(id);
        log.info("게시글 삭제: id={}", id);
    }

    /**
     * 카테고리 삭제 시 게시글과 업로드 파일을 함께 정리합니다.
     */
    @Transactional
    public int deletePostsInCategory(String categoryId) {
        List<Post> posts = postRepository.findByCategoryId(categoryId);
        for (Post post : posts) {
            deleteFilesOf(post);
        }
        postRepository.deleteAll(posts);
        return posts.size();
    }

    @Transactional
    public void reorderPosts(List<String> ids) {
        int order = 0;
        for (String rawId : ids) {
            Long id;
            try {
                id = Long.parseLong(rawId);
            } catch (NumberFormatException e) {
                throw new RuntimeException("잘못된 게시글 ID: " + rawId);
            }
            Post post = getPostById(id);
            post.setDisplayOrder(order++);
            postRepository.save(post);
        }
        log.info("게시글 순서 변경: {}개", ids.size());
    }

    private void deleteFilesOf(Post post) {
        Set<String> files = new HashSet<>();
        if (post.getThumbnail() != null) {
            files.add(post.getThumbnail());
        }
        if (post.getImages() != null) {
            for (PostImage img : post.getImages()) {
                if (img.getImageUrl() != null && !"VIDEO".equals(img.getMediaType())) {
                    files.add(img.getImageUrl());
                }
            }
        }
        fileStorageService.deleteFiles(new ArrayList<>(files));
    }

    private void applyRequest(Post post, PostRequest request) {
        post.setCategoryId(request.getCategoryId());
        post.setContentType(request.getContentType());
        post.setTitle(request.getTitle().trim());
        post.setYear(nullToEmpty(request.getYear()));
        post.setMedium(nullToEmpty(request.getMedium()));
        post.setSize(nullToEmpty(request.getSize()));
        post.setThumbnail(request.getThumbnail());
        post.setDescription(request.getDescription());
        post.setVideoUrl(request.getVideoUrl());
        post.setHtmlContent(request.getHtmlContent());
        post.setImages(request.getImages() != null ? request.getImages() : new ArrayList<>());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
