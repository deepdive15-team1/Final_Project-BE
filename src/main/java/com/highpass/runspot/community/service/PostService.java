package com.highpass.runspot.community.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;
import com.highpass.runspot.community.domain.PostSort;
import com.highpass.runspot.community.domain.PostStatus;
import com.highpass.runspot.community.dto.PostDetailResponse;
import com.highpass.runspot.community.dto.PostListResponse;
import com.highpass.runspot.community.dto.PostSummaryResponse;
import com.highpass.runspot.community.dto.PostUpsertRequest;
import com.highpass.runspot.community.exception.CommunityErrorCode;
import com.highpass.runspot.community.exception.CommunityException;
import com.highpass.runspot.community.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostListResponse getPosts(BoardType boardType, PostSort sort, String query, String cursor, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        Page<Post> page = sort == PostSort.POPULAR
                ? findPopular(boardType, normalizedQuery, cursor, pageSize)
                : findLatest(boardType, normalizedQuery, cursor, pageSize);
        List<PostSummaryResponse> items = page.getContent().stream().map(PostSummaryResponse::from).toList();
        Post last = page.getContent().isEmpty() ? null : page.getContent().get(page.getContent().size() - 1);
        String nextCursor = last == null ? null
                : sort == PostSort.POPULAR ? last.getLikeCount() + "_" + last.getId() : last.getId().toString();
        return new PostListResponse(items, nextCursor, page.hasNext());
    }

    @Transactional
    public PostDetailResponse getPost(Long postId, Long viewerId) {
        Post post = findPublishedPost(postId);
        post.increaseViewCount();
        return PostDetailResponse.from(post, viewerId);
    }

    @Transactional
    public PostDetailResponse createPost(Long userId, PostUpsertRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.USER_NOT_FOUND));
        Post post = Post.create(author, request.boardType(), request.title(), request.content(),
                request.runningRecordId(), request.status(), request.imageKeys());
        return PostDetailResponse.from(postRepository.save(post), userId);
    }

    @Transactional
    public PostDetailResponse updatePost(Long userId, Long postId, PostUpsertRequest request) {
        Post post = findActivePost(postId);
        post.validateOwner(userId);
        post.update(request.boardType(), request.title(), request.content(), request.runningRecordId(),
                request.status(), request.imageKeys());
        return PostDetailResponse.from(post, userId);
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = findActivePost(postId);
        post.validateOwner(userId);
        post.delete();
    }

    private Page<Post> findLatest(BoardType boardType, String query, String cursor, int size) {
        Long cursorId = cursor == null ? null : parseLong(cursor);
        return postRepository.findLatest(boardType, query, cursorId, PageRequest.of(0, size));
    }

    private Page<Post> findPopular(BoardType boardType, String query, String cursor, int size) {
        if (cursor == null) {
            return postRepository.findPopular(boardType, query, null, null, PageRequest.of(0, size));
        }
        String[] parts = cursor.split("_", -1);
        if (parts.length != 2) {
            throw new CommunityException(CommunityErrorCode.INVALID_CURSOR);
        }
        try {
            return postRepository.findPopular(boardType, query, Integer.valueOf(parts[0]), Long.valueOf(parts[1]),
                    PageRequest.of(0, size));
        } catch (NumberFormatException e) {
            throw new CommunityException(CommunityErrorCode.INVALID_CURSOR);
        }
    }

    private Long parseLong(String cursor) {
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException e) {
            throw new CommunityException(CommunityErrorCode.INVALID_CURSOR);
        }
    }

    private Post findPublishedPost(Long postId) {
        return postRepository.findDetailByIdAndStatus(postId, PostStatus.PUBLISHED)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private Post findActivePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        if (post.getStatus() == PostStatus.DELETED) {
            throw new CommunityException(CommunityErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
