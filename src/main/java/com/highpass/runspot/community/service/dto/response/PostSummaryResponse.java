package com.highpass.runspot.community.service.dto.response;

import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;

import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

public record PostSummaryResponse(
        Long postId,
        BoardType boardType,
        String title,
        String content,
        String authorName,
        String thumbnailKey,
        String thumbnailUrl,
        int likeCount,
        int commentCount,
        int viewCount,
        boolean liked,
        LocalDateTime createdAt) {
    /**
     * @param viewUrl imageKey를 비공개 버킷용 presigned GET URL로 바꾸는 함수
     */
    public static PostSummaryResponse from(
            Post post, boolean liked, UnaryOperator<String> viewUrl) {
        String thumbnailKey =
                post.getImages().isEmpty() ? null : post.getImages().get(0).getImageKey();
        return new PostSummaryResponse(
                post.getId(),
                post.getBoardType(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getName(),
                thumbnailKey,
                thumbnailKey == null ? null : viewUrl.apply(thumbnailKey),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                liked,
                post.getCreatedAt());
    }
}
