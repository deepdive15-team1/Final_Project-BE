package com.highpass.runspot.community.dto;

import com.highpass.runspot.community.domain.Comment;
import com.highpass.runspot.community.domain.CommentStatus;

import java.time.LocalDateTime;

public record CommentResponse(
        Long commentId,
        Long parentId,
        Long authorId,
        String authorName,
        String content,
        CommentStatus status,
        LocalDateTime createdAt) {
    private static final String DELETED_COMMENT_PLACEHOLDER = "삭제된 댓글입니다.";

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getStatus() == CommentStatus.DELETED
                        ? DELETED_COMMENT_PLACEHOLDER
                        : comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt());
    }
}
