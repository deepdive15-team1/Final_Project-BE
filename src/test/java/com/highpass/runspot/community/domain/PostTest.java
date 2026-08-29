package com.highpass.runspot.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.community.exception.CommunityErrorCode;
import com.highpass.runspot.community.exception.CommunityException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostTest {

    private final User author = User.builder().id(1L).name("작성자").build();

    @Test
    void 코스_게시글은_러닝_기록이_필수다() {
        assertThatThrownBy(() -> Post.create(author, BoardType.COURSE, "제목", "내용", null,
                PostStatus.PUBLISHED, List.of()))
                .isInstanceOf(CommunityException.class)
                .hasFieldOrPropertyWithValue("exceptionType", CommunityErrorCode.INVALID_COURSE_POST);
    }

    @Test
    void 일반_게시글에는_러닝_기록을_연결할_수_없다() {
        assertThatThrownBy(() -> Post.create(author, BoardType.GENERAL, "제목", "내용", 10L,
                PostStatus.PUBLISHED, List.of()))
                .isInstanceOf(CommunityException.class)
                .hasFieldOrPropertyWithValue("exceptionType", CommunityErrorCode.INVALID_GENERAL_POST);
    }

    @Test
    void 이미지는_정렬_순서와_함께_최대_세장까지_등록된다() {
        Post post = Post.create(author, BoardType.GENERAL, "제목", "내용", null,
                PostStatus.PUBLISHED, List.of("one.jpg", "two.jpg", "three.jpg"));

        assertThat(post.getImages()).extracting(PostImage::getSortOrder).containsExactly(0, 1, 2);
        assertThat(post.getImages()).extracting(PostImage::getImageKey)
                .containsExactly("one.jpg", "two.jpg", "three.jpg");
    }

    @Test
    void 이미지는_세장을_초과할_수_없다() {
        assertThatThrownBy(() -> Post.create(author, BoardType.GENERAL, "제목", "내용", null,
                PostStatus.PUBLISHED, List.of("1", "2", "3", "4")))
                .isInstanceOf(CommunityException.class)
                .hasFieldOrPropertyWithValue("exceptionType", CommunityErrorCode.INVALID_IMAGE_COUNT);
    }

    @Test
    void 작성자만_게시글을_변경할_수_있다() {
        Post post = Post.create(author, BoardType.GENERAL, "제목", "내용", null,
                PostStatus.PUBLISHED, List.of());

        assertThatThrownBy(() -> post.validateOwner(2L))
                .isInstanceOf(CommunityException.class)
                .hasFieldOrPropertyWithValue("exceptionType", CommunityErrorCode.NOT_POST_OWNER);
    }

    @Test
    void 게시글_삭제는_소프트_삭제로_처리한다() {
        Post post = Post.create(author, BoardType.GENERAL, "제목", "내용", null,
                PostStatus.PUBLISHED, List.of());

        post.delete();

        assertThat(post.getStatus()).isEqualTo(PostStatus.DELETED);
    }

    @Test
    void 좋아요와_댓글_카운터는_음수가_되지_않는다() {
        Post post = Post.create(author, BoardType.GENERAL, "제목", "내용", null,
                PostStatus.PUBLISHED, List.of());

        post.increaseLikeCount();
        post.decreaseLikeCount();
        post.decreaseLikeCount();
        post.increaseCommentCount();
        post.decreaseCommentCount();
        post.decreaseCommentCount();

        assertThat(post.getLikeCount()).isZero();
        assertThat(post.getCommentCount()).isZero();
    }
}
