package com.highpass.runspot.community.service;

import com.highpass.runspot.community.domain.Post;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PostViewService {
    private static final Duration VIEW_DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public void increase(Post post, Long userId) {
        if (userId == null) {
            post.increaseViewCount();
            return;
        }
        try {
            boolean firstViewToday =
                    Boolean.TRUE.equals(
                            redis.opsForValue()
                                    .setIfAbsent(viewKey(post.getId(), userId), "1", VIEW_DEDUP_TTL));
            if (firstViewToday) {
                post.increaseViewCount();
            }
        } catch (DataAccessException redisUnavailable) {
            post.increaseViewCount();
        }
    }

    private String viewKey(Long postId, Long userId) {
        return "post:view:" + postId + ":" + userId;
    }
}
