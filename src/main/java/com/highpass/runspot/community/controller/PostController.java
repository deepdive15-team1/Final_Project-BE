package com.highpass.runspot.community.controller;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.PostSort;
import com.highpass.runspot.community.dto.PostDetailResponse;
import com.highpass.runspot.community.dto.PostListResponse;
import com.highpass.runspot.community.dto.PostUpsertRequest;
import com.highpass.runspot.community.service.PostService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
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
@Tag(name = "Community Post", description = "커뮤니티 게시글 API")
public class PostController {

    private final PostService postService;

    @GetMapping("/drafts")
    @Operation(summary = "임시저장 게시글 목록", description = "로그인 사용자의 임시저장 글을 최근 수정순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<List<PostDetailResponse>> getDrafts(@AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        return ResponseEntity.ok(postService.getDrafts(principal.getId()));
    }

    @PostMapping("/drafts")
    @Operation(summary = "게시글 임시저장", description = "요청 status와 관계없이 DRAFT 상태로 저장합니다.")
    @ApiResponse(responseCode = "201", description = "임시저장 성공")
    public ResponseEntity<PostDetailResponse> createDraft(
            @Valid @RequestBody PostUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        PostDetailResponse response = postService.createDraft(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + response.postId())).body(response);
    }

    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "게시판 유형·정렬·검색 조건으로 게시글을 커서 기반 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<PostListResponse> getPosts(
            @RequestParam(required = false) BoardType boardType,
            @RequestParam(defaultValue = "LATEST") PostSort sort,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getPosts(boardType, sort, query, cursor, size));
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 상세와 로그인 사용자의 좋아요·스크랩 상태를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "게시글 없음")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(postService.getPost(postId, principal == null ? null : principal.getId()));
    }

    @PostMapping
    @Operation(summary = "게시글 작성", description = "일반 또는 러닝 코스 게시글을 작성하거나 임시 저장합니다.")
    @ApiResponse(responseCode = "201", description = "작성 성공")
    public ResponseEntity<PostDetailResponse> createPost(
            @Valid @RequestBody PostUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        PostDetailResponse response = postService.createPost(principal.getId(), request);
        return ResponseEntity.created(URI.create("/api/v1/posts/" + response.postId())).body(response);
    }

    @PatchMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "작성자가 게시글 내용과 이미지를 수정합니다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "403", description = "작성자 권한 없음")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        return ResponseEntity.ok(postService.updatePost(principal.getId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "작성자가 게시글을 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
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
