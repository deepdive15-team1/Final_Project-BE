package com.highpass.runspot.community.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.domain.*;
import com.highpass.runspot.community.service.dto.request.*;
import com.highpass.runspot.community.service.dto.response.*;
import com.highpass.runspot.community.exception.*;
import com.highpass.runspot.community.domain.dao.*;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityInteractionService {
    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final PostScrapRepository scraps;
    private final CommentRepository comments;
    private final ReportRepository reports;
    private final UserRepository users;

    @Transactional
    public void like(Long userId, Long postId) {
        if (likes.existsByPostIdAndUserId(postId, userId)) {
            throw error(CommunityErrorCode.ALREADY_LIKED);
        }
        Post post = post(postId);
        try {
            likes.saveAndFlush(PostLike.create(post, user(userId)));
        } catch (DataIntegrityViolationException duplicateLike) {
            throw error(CommunityErrorCode.ALREADY_LIKED);
        }
        posts.incrementLikeCount(postId);
    }

    @Transactional
    public void unlike(Long userId, Long postId) {
        post(postId);
        // 재시도·동시 취소도 성공 처리하며 실제 삭제한 요청만 카운트를 감소시킨다.
        if (likes.deleteLike(postId, userId) > 0) {
            posts.decrementLikeCount(postId);
        }
    }

    @Transactional
    public void scrap(Long userId, Long postId) {
        if (scraps.existsByPostIdAndUserId(postId, userId)) {
            throw error(CommunityErrorCode.ALREADY_SCRAPPED);
        }
        try {
            scraps.saveAndFlush(PostScrap.create(post(postId), user(userId)));
        } catch (DataIntegrityViolationException duplicateScrap) {
            throw error(CommunityErrorCode.ALREADY_SCRAPPED);
        }
    }

    @Transactional
    public void unscrap(Long userId, Long postId) {
        scraps.delete(
                scraps.findByPostIdAndUserId(postId, userId)
                        .orElseThrow(() -> error(CommunityErrorCode.SCRAP_NOT_FOUND)));
    }

    public List<PostSummaryResponse> myScraps(Long userId) {
        List<Post> savedPosts = scraps.findByUserIdOrderByIdDesc(userId).stream()
                .map(PostScrap::getPost)
                .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                .toList();
        Set<Long> likedIds = savedPosts.isEmpty() ? Set.of()
                : likes.findLikedPostIds(userId, savedPosts.stream().map(Post::getId).toList());
        return savedPosts.stream().map(post -> PostSummaryResponse.from(post, likedIds.contains(post.getId())))
                .toList();
    }

    public List<CommentResponse> comments(Long postId) {
        post(postId);
        return comments.findByPostIdOrderByIdAsc(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse comment(Long userId, Long postId, CommentRequest request) {
        Post post = post(postId);
        Comment parent = findParentComment(postId, request.parentId());
        Comment saved =
                comments.save(Comment.create(post, user(userId), parent, request.content()));
        posts.incrementCommentCount(postId);
        return CommentResponse.from(saved);
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment =
                comments.findById(commentId)
                        .orElseThrow(() -> error(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (comment.getStatus() == CommentStatus.ACTIVE) {
            comment.delete(userId);
            posts.decrementCommentCount(comment.getPost().getId());
        }
    }

    @Transactional
    public Long report(Long userId, ReportRequest request) {
        boolean alreadyReported =
                reports.existsByReporterIdAndTargetTypeAndTargetId(
                        userId, request.targetType(), request.targetId());
        if (alreadyReported) {
            throw error(CommunityErrorCode.ALREADY_REPORTED);
        }
        try {
            Report report =
                    Report.create(
                            user(userId),
                            request.targetType(),
                            request.targetId(),
                            request.reasonCode(),
                            request.detail());
            return reports.saveAndFlush(report).getId();
        } catch (DataIntegrityViolationException duplicateReport) {
            throw error(CommunityErrorCode.ALREADY_REPORTED);
        }
    }

    private Comment findParentComment(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent =
                comments.findById(parentId)
                        .orElseThrow(() -> error(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (parent.getParent() != null) {
            throw error(CommunityErrorCode.NESTED_REPLY_NOT_ALLOWED);
        }
        if (!parent.getPost().getId().equals(postId)) {
            throw error(CommunityErrorCode.PARENT_COMMENT_MISMATCH);
        }
        return parent;
    }

    private Post post(Long postId) {
        Post post = posts.findById(postId).orElseThrow(() -> error(CommunityErrorCode.POST_NOT_FOUND));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw error(CommunityErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private User user(Long userId) {
        return users.findById(userId).orElseThrow(() -> error(CommunityErrorCode.USER_NOT_FOUND));
    }

    private CommunityException error(CommunityErrorCode code) {
        return new CommunityException(code);
    }
}
