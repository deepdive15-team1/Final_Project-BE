package com.highpass.runspot.community.dto;
import com.highpass.runspot.community.domain.Comment;
import com.highpass.runspot.community.domain.CommentStatus;
import java.time.LocalDateTime;
public record CommentResponse(Long commentId,Long parentId,Long authorId,String authorName,String content,CommentStatus status,LocalDateTime createdAt){public static CommentResponse from(Comment c){return new CommentResponse(c.getId(),c.getParent()==null?null:c.getParent().getId(),c.getAuthor().getId(),c.getAuthor().getName(),c.getStatus()==CommentStatus.DELETED?"삭제된 댓글입니다.":c.getContent(),c.getStatus(),c.getCreatedAt());}}
