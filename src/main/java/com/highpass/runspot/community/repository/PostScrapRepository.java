package com.highpass.runspot.community.repository;

import com.highpass.runspot.community.domain.PostScrap;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostScrapRepository extends JpaRepository<PostScrap, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostScrap> findByPostIdAndUserId(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "post.author", "post.images"})
    List<PostScrap> findByUserIdOrderByIdDesc(Long userId);
}
