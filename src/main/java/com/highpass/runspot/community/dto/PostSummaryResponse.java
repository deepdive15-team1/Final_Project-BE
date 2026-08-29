package com.highpass.runspot.community.dto;

import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;

import java.time.LocalDateTime;

public record PostSummaryResponse(
        Long postId,
        BoardType boardType,
        String title,
        String content,
        String authorName,
        String thumbnailKey,
        int likeCount,
        int commentCount,
        int viewCount,
        LocalDateTime createdAt) {
    public static PostSummaryResponse from(Post post) {
        String thumbnailKey =
                post.getImages().isEmpty() ? null : post.getImages().get(0).getImageKey();
        return new PostSummaryResponse(
                post.getId(),
                post.getBoardType(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getName(),
                thumbnailKey,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt());
    }
}
