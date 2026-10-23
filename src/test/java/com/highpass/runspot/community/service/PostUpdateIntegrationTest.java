package com.highpass.runspot.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.*;
import com.highpass.runspot.community.domain.*;
import com.highpass.runspot.community.service.dto.request.PostUpsertRequest;
import com.highpass.runspot.file.domain.UploadedFile;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class PostUpdateIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PostService service;

    // 프론트가 상세 조회로 받은 imageKey를 그대로 돌려보내는 흐름. 과거에는 기존 이미지를 전부 지우고
    // 같은 sort_order로 다시 INSERT하면서 uk_post_images_post_order 위반으로 500이 났다.
    @Test
    void 이미지를_그대로_두고_제목과_내용만_수정할_수_있다() {
        User author = User.builder().username("same-image-author").password("password").name("author")
                .ageGroup(AgeGroup.TWENTIES).gender(Gender.MALE).build();
        em.persist(author);
        for (String key : List.of("kept-first.jpg", "kept-second.jpg")) {
            em.persist(UploadedFile.pending(key, author));
        }
        Post post = Post.create(author, BoardType.GENERAL, "before", "before content", null,
                PostStatus.PUBLISHED, List.of("kept-first.jpg", "kept-second.jpg"));
        em.persist(post);
        em.flush();
        em.clear();

        service.updatePost(author.getId(), post.getId(), new PostUpsertRequest(
                BoardType.GENERAL, "after", "after content", null,
                List.of("kept-first.jpg", "kept-second.jpg"), List.of(), PostStatus.PUBLISHED));
        em.flush();
        em.clear();

        Post updated = em.find(Post.class, post.getId());
        assertThat(updated.getTitle()).isEqualTo("after");
        assertThat(updated.getContent()).isEqualTo("after content");
        assertThat(updated.getImages()).extracting(PostImage::getImageKey)
                .containsExactly("kept-first.jpg", "kept-second.jpg");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateExistingImagesAndTagsWithoutUniqueConstraintConflicts(boolean withImages) {
        User author = User.builder().username("update-author").password("password").name("author")
                .ageGroup(AgeGroup.TWENTIES).gender(Gender.MALE).build();
        em.persist(author);
        Tag retained = Tag.create("running");
        Tag removed = Tag.create("old");
        Tag added = Tag.create("new");
        em.persist(retained);
        em.persist(removed);
        em.persist(added);
        for (String key : List.of("first.jpg", "second.jpg", "new.jpg")) {
            em.persist(UploadedFile.pending(key, author));
        }
        Post post = Post.create(author, BoardType.GENERAL, "title", "content", null,
                PostStatus.PUBLISHED, withImages ? List.of("first.jpg", "second.jpg") : List.of());
        post.replaceTags(List.of(retained, removed));
        em.persist(post);
        em.flush();
        em.clear();

        service.updatePost(author.getId(), post.getId(), new PostUpsertRequest(
                BoardType.GENERAL, "edited", "edited content", null,
                withImages ? List.of("second.jpg", "new.jpg") : List.of(),
                List.of("running", "new"), PostStatus.PUBLISHED));
        em.flush();
        em.clear();

        Post updated = em.find(Post.class, post.getId());
        assertThat(updated.getTitle()).isEqualTo("edited");
        assertThat(updated.getImages()).extracting(PostImage::getImageKey)
                .containsExactlyElementsOf(withImages ? List.of("second.jpg", "new.jpg") : List.of());
        assertThat(updated.getPostTags()).extracting(pt -> pt.getTag().getName())
                .containsExactlyInAnyOrder("running", "new");
        assertThat(em.find(Tag.class, retained.getId()).getUsageCount()).isEqualTo(1);
        assertThat(em.find(Tag.class, removed.getId()).getUsageCount()).isZero();
        assertThat(em.find(Tag.class, added.getId()).getUsageCount()).isEqualTo(1);

        service.updatePost(author.getId(), post.getId(), new PostUpsertRequest(
                BoardType.GENERAL, "edited again", "content", null,
                List.of("new.jpg"), List.of("running", "new"), PostStatus.PUBLISHED));
        em.flush();
        em.clear();
        assertThat(em.find(Post.class, post.getId()).getImages()).extracting(PostImage::getImageKey)
                .containsExactly("new.jpg");
        assertThat(em.find(Tag.class, retained.getId()).getUsageCount()).isEqualTo(1);

        service.updatePost(author.getId(), post.getId(), new PostUpsertRequest(
                BoardType.GENERAL, "cleared", "content", null,
                List.of(), List.of(), PostStatus.PUBLISHED));
        em.flush();
        em.clear();
        Post cleared = em.find(Post.class, post.getId());
        assertThat(cleared.getImages()).isEmpty();
        assertThat(cleared.getPostTags()).isEmpty();
        assertThat(em.find(Tag.class, retained.getId()).getUsageCount()).isZero();
        assertThat(em.find(Tag.class, added.getId()).getUsageCount()).isZero();
    }
}
