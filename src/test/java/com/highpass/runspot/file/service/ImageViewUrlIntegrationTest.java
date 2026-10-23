package com.highpass.runspot.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;
import com.highpass.runspot.community.domain.PostStatus;
import com.highpass.runspot.community.service.PostService;
import com.highpass.runspot.community.service.dto.response.PostDetailResponse;
import com.highpass.runspot.file.domain.UploadedFile;
import com.highpass.runspot.file.service.dto.response.ImageViewUrlResponse;
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
class ImageViewUrlIntegrationTest {
    private static final String KEY = "posts/2026/10/24/view-me.jpg";

    @Autowired EntityManager em;
    @Autowired S3PresignService presignService;
    @Autowired PostService postService;

    @Test
    void 업로드된_키에_대해_서명된_조회_URL을_발급한다() {
        em.persist(UploadedFile.pending(KEY, author("view-url-owner")));
        em.flush();

        ImageViewUrlResponse response = presignService.viewUrls(List.of(KEY, KEY));

        assertThat(response.items()).hasSize(1);
        ImageViewUrlResponse.Item item = response.items().get(0);
        assertThat(item.imageKey()).isEqualTo(KEY);
        assertThat(item.expiresIn()).isEqualTo(3600L);
        assertThat(item.viewUrl()).contains("posts/2026/10/24/view-me.jpg")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=3600");
    }

    @Test
    void 게시글_상세는_imageKey와_함께_조회_URL을_내려준다() {
        User author = author("detail-url-owner");
        em.persist(UploadedFile.pending(KEY, author));
        Post post = Post.create(author, BoardType.GENERAL, "title", "content", null,
                PostStatus.PUBLISHED, List.of(KEY));
        em.persist(post);
        em.flush();
        em.clear();

        PostDetailResponse detail = postService.getPost(post.getId(), author.getId());

        assertThat(detail.imageKeys()).containsExactly(KEY);
        assertThat(detail.imageUrls()).hasSize(1);
        assertThat(detail.imageUrls().get(0)).startsWith("https://").contains("X-Amz-Signature=");
    }

    private User author(String username) {
        User user = User.builder().username(username).password("password").name("author")
                .ageGroup(AgeGroup.TWENTIES).gender(Gender.MALE).build();
        em.persist(user);
        return user;
    }
}
