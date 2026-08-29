package com.highpass.runspot.community.domain;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.common.domain.BaseTimeEntity;
import com.highpass.runspot.community.exception.CommunityErrorCode;
import com.highpass.runspot.community.exception.CommunityException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_posts_user"))
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "running_record_id")
    private Long runningRecordId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images = new ArrayList<>();

    public static Post create(User author, BoardType boardType, String title, String content,
                              Long runningRecordId, PostStatus status, List<String> imageKeys) {
        Post post = new Post();
        post.author = author;
        post.update(boardType, title, content, runningRecordId, status, imageKeys);
        return post;
    }

    public void update(BoardType boardType, String title, String content, Long runningRecordId,
                       PostStatus status, List<String> imageKeys) {
        validateCourse(boardType, runningRecordId);
        validateImages(imageKeys);
        this.boardType = boardType;
        this.title = title;
        this.content = content;
        this.runningRecordId = runningRecordId;
        this.status = status;
        this.images.clear();
        for (int i = 0; i < imageKeys.size(); i++) {
            this.images.add(PostImage.create(this, imageKeys.get(i), i));
        }
    }

    public void validateOwner(Long userId) {
        if (!Objects.equals(author.getId(), userId)) {
            throw new CommunityException(CommunityErrorCode.NOT_POST_OWNER);
        }
    }

    public void delete() {
        this.status = PostStatus.DELETED;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void increaseLikeCount() { this.likeCount++; }

    public void decreaseLikeCount() { if (this.likeCount > 0) this.likeCount--; }

    public void increaseCommentCount() { this.commentCount++; }

    public void decreaseCommentCount() { if (this.commentCount > 0) this.commentCount--; }

    private static void validateCourse(BoardType boardType, Long runningRecordId) {
        if (boardType == BoardType.COURSE && runningRecordId == null) {
            throw new CommunityException(CommunityErrorCode.INVALID_COURSE_POST);
        }
        if (boardType == BoardType.GENERAL && runningRecordId != null) {
            throw new CommunityException(CommunityErrorCode.INVALID_GENERAL_POST);
        }
    }

    private static void validateImages(List<String> imageKeys) {
        if (imageKeys.size() > 3) {
            throw new CommunityException(CommunityErrorCode.INVALID_IMAGE_COUNT);
        }
    }
}
