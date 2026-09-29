package com.highpass.runspot.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.*;
import com.highpass.runspot.community.domain.*;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class PostDraftIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PostService service;

    @Test
    void draftsIncludeImagesAndTagsAndExcludeOtherAuthorsAndStatuses() {
        User author = author("draft-author");
        User other = author("other-author");
        Tag tag = Tag.create("러닝");
        em.persist(tag);
        Post draft = post(author, PostStatus.DRAFT);
        draft.replaceTags(List.of(tag));
        post(author, PostStatus.PUBLISHED);
        post(author, PostStatus.DELETED);
        post(other, PostStatus.DRAFT);
        em.flush();
        em.clear();

        var result = service.getDrafts(author.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).postId()).isEqualTo(draft.getId());
        assertThat(result.get(0).imageKeys()).containsExactly("first.jpg", "second.jpg");
        assertThat(result.get(0).tags()).containsExactly("러닝");
        assertThat(result.get(0).mine()).isTrue();
        assertThat(service.getDrafts(Long.MAX_VALUE)).isEmpty();
    }

    private User author(String username) {
        User user = User.builder().username(username).password("password").name(username)
                .ageGroup(AgeGroup.TWENTIES).gender(Gender.MALE).build();
        em.persist(user);
        return user;
    }

    private Post post(User author, PostStatus status) {
        Post post = Post.create(author, BoardType.GENERAL, "제목", "내용", null, status,
                List.of("first.jpg", "second.jpg"));
        em.persist(post);
        return post;
    }
}
