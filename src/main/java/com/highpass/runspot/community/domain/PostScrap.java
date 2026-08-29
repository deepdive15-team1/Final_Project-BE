package com.highpass.runspot.community.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Entity @Table(name = "post_scraps", uniqueConstraints = @UniqueConstraint(name = "uk_post_scraps_post_user", columnNames = {"post_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostScrap extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    public static PostScrap create(Post post, User user) { PostScrap value = new PostScrap(); value.post = post; value.user = user; return value; }
}
