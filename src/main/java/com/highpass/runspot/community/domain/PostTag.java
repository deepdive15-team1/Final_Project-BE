package com.highpass.runspot.community.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "post_tags",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_post_tags_post_tag",
                        columnNames = {"post_id", "tag_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    public static PostTag create(Post post, Tag tag) {
        PostTag postTag = new PostTag();
        postTag.post = post;
        postTag.tag = tag;
        return postTag;
    }
}
