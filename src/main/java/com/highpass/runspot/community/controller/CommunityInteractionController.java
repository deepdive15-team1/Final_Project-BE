package com.highpass.runspot.community.controller;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.community.dto.*;
import com.highpass.runspot.community.service.CommunityInteractionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Community Interaction", description = "좋아요·스크랩·댓글·신고 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommunityInteractionController {
    private final CommunityInteractionService service;

    @Operation(summary = "게시글 좋아요")
    @ApiResponse(responseCode = "204", description = "좋아요 성공")
    @ApiResponse(responseCode = "409", description = "중복 좋아요")
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Void> like(
            @PathVariable Long postId, @AuthenticationPrincipal UserPrincipal principal) {
        service.like(userId(principal), postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "게시글 좋아요 취소")
    @ApiResponse(responseCode = "204", description = "취소 성공")
    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlike(
            @PathVariable Long postId, @AuthenticationPrincipal UserPrincipal principal) {
        service.unlike(userId(principal), postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관심 게시글 저장")
    @ApiResponse(responseCode = "204", description = "저장 성공")
    @ApiResponse(responseCode = "409", description = "중복 저장")
    @PostMapping("/posts/{postId}/scrap")
    public ResponseEntity<Void> scrap(
            @PathVariable Long postId, @AuthenticationPrincipal UserPrincipal principal) {
        service.scrap(userId(principal), postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "관심 게시글 저장 해제")
    @ApiResponse(responseCode = "204", description = "해제 성공")
    @DeleteMapping("/posts/{postId}/scrap")
    public ResponseEntity<Void> unscrap(
            @PathVariable Long postId, @AuthenticationPrincipal UserPrincipal principal) {
        service.unscrap(userId(principal), postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내가 저장한 게시글 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/me/scraps")
    public ResponseEntity<List<PostSummaryResponse>> myScraps(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.myScraps(userId(principal)));
    }

    @Operation(summary = "댓글 목록 조회", description = "삭제된 댓글을 포함해 댓글과 1단계 대댓글을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> comments(@PathVariable Long postId) {
        return ResponseEntity.ok(service.comments(postId));
    }

    @Operation(summary = "댓글 또는 대댓글 작성", description = "parentId가 있으면 1단계 대댓글을 작성합니다.")
    @ApiResponse(responseCode = "201", description = "작성 성공")
    @ApiResponse(responseCode = "400", description = "2단계 초과 대댓글")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> comment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CommentResponse response = service.comment(userId(principal), postId, request);
        return ResponseEntity.created(URI.create("/api/v1/comments/" + response.commentId()))
                .body(response);
    }

    @Operation(summary = "댓글 삭제", description = "댓글 트리 유지를 위해 소프트 삭제합니다.")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "403", description = "작성자 권한 없음")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long commentId, @AuthenticationPrincipal UserPrincipal principal) {
        service.deleteComment(userId(principal), commentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "대상 신고", description = "게시글·댓글·채팅 메시지·사용자를 신고합니다.")
    @ApiResponse(responseCode = "201", description = "신고 접수")
    @ApiResponse(responseCode = "409", description = "중복 신고")
    @PostMapping("/reports")
    public ResponseEntity<Void> report(
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long reportId = service.report(userId(principal), request);
        return ResponseEntity.created(URI.create("/api/v1/reports/" + reportId)).build();
    }

    private Long userId(UserPrincipal principal) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        return principal.getId();
    }
}
