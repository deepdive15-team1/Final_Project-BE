package com.highpass.runspot.community.repository;
import com.highpass.runspot.community.domain.PostLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PostLikeRepository extends JpaRepository<PostLike,Long>{boolean existsByPostIdAndUserId(Long postId,Long userId); Optional<PostLike> findByPostIdAndUserId(Long postId,Long userId);}
