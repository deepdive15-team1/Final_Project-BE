package com.highpass.runspot.community.domain.dao;

import com.highpass.runspot.community.domain.PostLike;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PostLike l where l.post.id = :postId and l.user.id = :userId")
    int deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query("select l.post.id from PostLike l where l.user.id = :userId and l.post.id in :postIds")
    Set<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
