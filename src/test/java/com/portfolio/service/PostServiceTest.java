package com.portfolio.service;

import com.portfolio.dto.PostRequest;
import com.portfolio.model.Post;
import com.portfolio.model.PostImage;
import com.portfolio.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게시글 저장·삭제 시 업로드 파일을 정리하는 규칙.
 *
 * 과거에 "썸네일이 바뀌면 이전 파일 삭제"로 구현했다가, 이미지 순서를 바꾸면
 * 첫 번째 이미지가 썸네일이 되면서 아직 쓰이는 파일이 지워지는 버그가 있었습니다.
 * 그 버그가 돌아오지 않는지 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private PostService postService;

    private Post existing;

    @BeforeEach
    void setUp() {
        existing = new Post();
        existing.setId(1L);
        existing.setCategoryId("artwork");
        existing.setContentType("PHOTO");
        existing.setTitle("기존 작품");
        existing.setThumbnail("a.jpg");
        existing.setImages(new ArrayList<>(Arrays.asList(image("a.jpg"), image("b.jpg"))));
    }

    private static PostImage image(String fileName) {
        PostImage img = new PostImage();
        img.setImageUrl(fileName);
        img.setMediaType("IMAGE");
        return img;
    }

    private static PostImage video(String url) {
        PostImage v = new PostImage();
        v.setImageUrl(url);
        v.setMediaType("VIDEO");
        return v;
    }

    private static PostRequest request(String thumbnail, PostImage... images) {
        PostRequest r = new PostRequest();
        r.setCategoryId("artwork");
        r.setContentType("PHOTO");
        r.setTitle("기존 작품");
        r.setThumbnail(thumbnail);
        r.setImages(new ArrayList<>(Arrays.asList(images)));
        return r;
    }

    @Test
    @DisplayName("이미지 순서만 바꾸면 어떤 파일도 지우지 않는다")
    void reorderingKeepsAllFiles() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        // b.jpg 가 맨 앞으로 올라오면서 썸네일이 바뀝니다.
        postService.updatePost(1L, request("b.jpg", image("b.jpg"), image("a.jpg")));

        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("목록에서 빠진 파일만 지운다")
    void removedFileIsDeleted() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        postService.updatePost(1L, request("a.jpg", image("a.jpg")));

        verify(fileStorageService).deleteFile("b.jpg");
        verify(fileStorageService, never()).deleteFile("a.jpg");
    }

    @Test
    @DisplayName("썸네일로만 쓰이고 목록에 없는 파일도 계속 쓰이면 지우지 않는다")
    void thumbnailOnlyFileIsKeptWhenStillReferenced() {
        existing.setThumbnail("cover.jpg");
        existing.setImages(new ArrayList<>(List.of(image("a.jpg"))));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        postService.updatePost(1L, request("cover.jpg", image("a.jpg")));

        verify(fileStorageService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("영상 항목은 파일이 아니므로 삭제를 시도하지 않는다")
    void videoEntriesAreNotDeleted() {
        existing.setImages(new ArrayList<>(Arrays.asList(
                image("a.jpg"), video("https://youtu.be/abcdefghijk"))));
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        postService.updatePost(1L, request("a.jpg", image("a.jpg")));

        verify(fileStorageService, never()).deleteFile("https://youtu.be/abcdefghijk");
    }

    @Test
    @DisplayName("게시글을 지우면 딸린 파일을 모두 지운다")
    void deletingPostRemovesItsFiles() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(existing));

        postService.deletePost(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<String>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(fileStorageService).deleteFiles(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("a.jpg", "b.jpg");
        verify(postRepository).deleteById(1L);
    }

    @Test
    @DisplayName("새 게시글은 해당 카테고리의 맨 뒤 순서를 받는다")
    void newPostGoesToTheEnd() {
        Post last = new Post();
        last.setDisplayOrder(4);
        when(postRepository.findTopByCategoryIdOrderByDisplayOrderDesc("artwork"))
                .thenReturn(Optional.of(last));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        Post created = postService.createPost(request("a.jpg", image("a.jpg")));

        assertThat(created.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("카테고리에 글이 없으면 첫 순서는 0이다")
    void firstPostInCategoryStartsAtZero() {
        when(postRepository.findTopByCategoryIdOrderByDisplayOrderDesc("artwork"))
                .thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        Post created = postService.createPost(request("a.jpg", image("a.jpg")));

        assertThat(created.getDisplayOrder()).isZero();
    }

    @Test
    @DisplayName("순서를 다시 매기면 받은 순서대로 0부터 번호가 붙는다")
    void reorderAssignsSequentialNumbers() {
        Post first = new Post();  first.setId(10L);
        Post second = new Post(); second.setId(20L);
        when(postRepository.findById(10L)).thenReturn(Optional.of(first));
        when(postRepository.findById(20L)).thenReturn(Optional.of(second));
        when(postRepository.save(any(Post.class))).thenAnswer(i -> i.getArgument(0));

        postService.reorderPosts(Arrays.asList("20", "10"));

        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
    }
}
