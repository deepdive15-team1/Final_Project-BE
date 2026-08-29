package com.highpass.runspot.community.dto;

import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;
import com.highpass.runspot.community.domain.PostStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long postId,
        BoardType boardType,
        String title,
        String content,
        AuthorResponse author,
        List<String> imageKeys,
        Long runningRecordId,
        int likeCount,
        int commentCount,
        int viewCount,
        PostStatus status,
        boolean liked,
        boolean scrapped,
        boolean mine,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PostDetailResponse from(Post post, Long viewerId, boolean liked, boolean scrapped) {
        return new PostDetailResponse(post.getId(), post.getBoardType(), post.getTitle(), post.getContent(),
                new AuthorResponse(post.getAuthor().getId(), post.getAuthor().getName(), post.getAuthor().getMannerTemp()),
                post.getImages().stream().map(image -> image.getImageKey()).toList(), post.getRunningRecordId(),
                post.getLikeCount(), post.getCommentCount(), post.getViewCount(), post.getStatus(), liked, scrapped,
                viewerId != null && viewerId.equals(post.getAuthor().getId()), post.getCreatedAt(), post.getUpdatedAt());
    }

    public record AuthorResponse(Long userId, String name, BigDecimal mannerTemperature) {
    }
}
