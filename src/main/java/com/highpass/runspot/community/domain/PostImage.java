package com.highpass.runspot.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "post_images", uniqueConstraints =
        @UniqueConstraint(name = "uk_post_images_post_order", columnNames = {"post_id", "sort_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_post_images_post"))
    private Post post;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    static PostImage create(Post post, String imageKey, int sortOrder) {
        PostImage image = new PostImage();
        image.post = post;
        image.imageKey = imageKey;
        image.sortOrder = sortOrder;
        return image;
    }
}
