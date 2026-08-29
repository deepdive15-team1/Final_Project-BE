package com.highpass.runspot.community.repository;

import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;
import com.highpass.runspot.community.domain.PostStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = {"author", "images", "postTags", "postTags.tag"})
    List<Post> findByAuthorIdAndStatusOrderByUpdatedAtDesc(Long authorId, PostStatus status);

    @EntityGraph(attributePaths = {"author", "images"})
    @Query("select distinct p from Post p where p.id = :id and p.status = :status")
    Optional<Post> findDetailByIdAndStatus(@Param("id") Long id, @Param("status") PostStatus status);

    @Query("""
            select p from Post p
            where p.status = com.highpass.runspot.community.domain.PostStatus.PUBLISHED
              and (:boardType is null or p.boardType = :boardType)
              and (:query is null or lower(p.title) like lower(concat('%', :query, '%'))
                   or lower(p.content) like lower(concat('%', :query, '%')))
              and (:cursorId is null or p.id < :cursorId)
            order by p.id desc
            """)
    Page<Post> findLatest(@Param("boardType") BoardType boardType, @Param("query") String query,
                          @Param("cursorId") Long cursorId, Pageable pageable);

    @Query("""
            select p from Post p
            where p.status = com.highpass.runspot.community.domain.PostStatus.PUBLISHED
              and (:boardType is null or p.boardType = :boardType)
              and (:query is null or lower(p.title) like lower(concat('%', :query, '%'))
                   or lower(p.content) like lower(concat('%', :query, '%')))
              and (:cursorLikeCount is null or p.likeCount < :cursorLikeCount
                   or (p.likeCount = :cursorLikeCount and p.id < :cursorId))
            order by p.likeCount desc, p.id desc
            """)
    Page<Post> findPopular(@Param("boardType") BoardType boardType, @Param("query") String query,
                           @Param("cursorLikeCount") Integer cursorLikeCount,
                           @Param("cursorId") Long cursorId, Pageable pageable);
}
