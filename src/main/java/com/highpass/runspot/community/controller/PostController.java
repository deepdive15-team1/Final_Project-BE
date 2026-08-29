package com.highpass.runspot.community.controller;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.PostSort;
import com.highpass.runspot.community.dto.PostDetailResponse;
import com.highpass.runspot.community.dto.PostListResponse;
import com.highpass.runspot.community.dto.PostUpsertRequest;
import com.highpass.runspot.community.service.PostService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<PostListResponse> getPosts(
            @RequestParam(required = false) BoardType boardType,
            @RequestParam(defaultValue = "LATEST") PostSort sort,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getPosts(boardType, sort, query, cursor, size));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(postService.getPost(postId, principal == null ? null : principal.getId()));
    }

    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(
            @Valid @RequestBody PostUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        PostDetailResponse response = postService.createPost(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + response.postId())).body(response);
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        return ResponseEntity.ok(postService.updatePost(principal.getId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        postService.deletePost(principal.getId(), postId);
        return ResponseEntity.noContent().build();
    }

    private void requireLogin(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
    }
}
