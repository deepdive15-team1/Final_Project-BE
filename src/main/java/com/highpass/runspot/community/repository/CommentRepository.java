package com.highpass.runspot.community.repository;
import com.highpass.runspot.community.domain.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CommentRepository extends JpaRepository<Comment,Long>{@EntityGraph(attributePaths={"author","parent"}) List<Comment> findByPostIdOrderByIdAsc(Long postId);}
