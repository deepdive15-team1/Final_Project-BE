package com.highpass.runspot.community.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import com.highpass.runspot.community.exception.CommunityErrorCode;
import com.highpass.runspot.community.exception.CommunityException;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentStatus status;

    public static Comment create(Post post, User author, Comment parent, String content) {
        Comment value = new Comment();
        value.post = post;
        value.author = author;
        value.parent = parent;
        value.content = content;
        value.status = CommentStatus.ACTIVE;
        return value;
    }

    public void delete(Long userId) {
        if (!Objects.equals(author.getId(), userId)) {
            throw new CommunityException(CommunityErrorCode.NOT_COMMENT_OWNER);
        }
        status = CommentStatus.DELETED;
    }
}
