package com.highpass.runspot.community.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.highpass.runspot.auth.domain.*;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.common.jwt.JwtProvider;
import com.highpass.runspot.community.domain.*;
import com.highpass.runspot.community.domain.dao.PostRepository;
import com.highpass.runspot.community.domain.dao.PostLikeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CommunityLikeApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PostLikeRepository likes;
    @Autowired JwtProvider jwt;

    @Test
    void repeatedUnlikeReturns204AndPreservesOtherUsersLikes() throws Exception {
        User author = user();
        User viewer = user();
        Post post = createPost(author, PostStatus.PUBLISHED);
        String path = "/api/v1/posts/" + post.getId() + "/like";
        mvc.perform(post(path).header("Authorization", token(author))).andExpect(status().isNoContent());
        mvc.perform(post(path).header("Authorization", token(viewer))).andExpect(status().isNoContent());
        mvc.perform(delete(path).header("Authorization", token(viewer))).andExpect(status().isNoContent());
        mvc.perform(delete(path).header("Authorization", token(viewer))).andExpect(status().isNoContent());
        assertThat(posts.findById(post.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
        assertThat(likes.existsByPostIdAndUserId(post.getId(), viewer.getId())).isFalse();
        assertThat(likes.existsByPostIdAndUserId(post.getId(), author.getId())).isTrue();
    }

    @Test
    void concurrentUnlikeDecrementsOnlyOnce() throws Exception {
        User author = user();
        User viewer = user();
        Post post = createPost(author, PostStatus.PUBLISHED);
        String path = "/api/v1/posts/" + post.getId() + "/like";
        mvc.perform(post(path).header("Authorization", token(author))).andExpect(status().isNoContent());
        mvc.perform(post(path).header("Authorization", token(viewer))).andExpect(status().isNoContent());
        String authorization = token(viewer);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 초과");
                    mvc.perform(delete(path).header("Authorization", authorization)).andExpect(status().isNoContent());
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertThat(posts.findById(post.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
        assertThat(likes.existsByPostIdAndUserId(post.getId(), viewer.getId())).isFalse();
        assertThat(likes.existsByPostIdAndUserId(post.getId(), author.getId())).isTrue();
    }

    @Test
    void duplicateLikeRemains409AndUnauthenticatedRequestsCannotMutate() throws Exception {
        User author = user();
        Post post = createPost(author, PostStatus.PUBLISHED);
        String path = "/api/v1/posts/" + post.getId() + "/like";
        mvc.perform(post(path)).andExpect(status().is4xxClientError());
        mvc.perform(post(path).header("Authorization", token(author))).andExpect(status().isNoContent());
        mvc.perform(post(path).header("Authorization", token(author))).andExpect(status().isConflict());
        mvc.perform(delete(path)).andExpect(status().is4xxClientError());
        assertThat(posts.findById(post.getId()).orElseThrow().getLikeCount()).isEqualTo(1);
        assertThat(likes.existsByPostIdAndUserId(post.getId(), author.getId())).isTrue();
    }

    @Test
    void savedPostListAlsoReportsCurrentUsersLikeState() throws Exception {
        User author = user();
        Post post = createPost(author, PostStatus.PUBLISHED);
        mvc.perform(post("/api/v1/posts/{id}/scrap", post.getId()).header("Authorization", token(author)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/posts/{id}/like", post.getId()).header("Authorization", token(author)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/scraps").header("Authorization", token(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].liked").value(true));
        mvc.perform(delete("/api/v1/posts/{id}/like", post.getId()).header("Authorization", token(author)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/scraps").header("Authorization", token(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].liked").value(false));
    }

    @Test
    void unlikeWithoutHistoryReturns204() throws Exception {
        User author = user();
        Post post = createPost(author, PostStatus.PUBLISHED);
        mvc.perform(delete("/api/v1/posts/{id}/like", post.getId())
                .header("Authorization", token(author))).andExpect(status().isNoContent());
        assertThat(posts.findById(post.getId()).orElseThrow().getLikeCount()).isZero();
    }

    @Test
    void unpublishedAndMissingPostsStillReturn404() throws Exception {
        User author = user();
        for (Long id : List.of(createPost(author, PostStatus.DRAFT).getId(),
                createPost(author, PostStatus.DELETED).getId(), Long.MAX_VALUE)) {
            mvc.perform(post("/api/v1/posts/{id}/like", id).header("Authorization", token(author)))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/posts/{id}/like", id).header("Authorization", token(author)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다."));
        }
    }

    @Test
    void listReportsCurrentUsersLikeStateInsteadOfInferringItFromCount() throws Exception {
        User author = user();
        User other = user();
        Post post = createPost(author, PostStatus.PUBLISHED);
        mvc.perform(post("/api/v1/posts/{id}/like", post.getId()).header("Authorization", token(author)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/posts").param("q", post.getTitle()).header("Authorization", token(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].liked").value(true));
        mvc.perform(get("/api/v1/posts").param("q", post.getTitle()).header("Authorization", token(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].liked").value(false))
                .andExpect(jsonPath("$.items[0].likeCount").value(1));
        mvc.perform(get("/api/v1/posts").param("q", post.getTitle()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].liked").value(false));
    }

    @Test
    void draftListReturnsImagesOutsideTestTransaction() throws Exception {
        User author = user();
        createPost(author, PostStatus.DRAFT);
        mvc.perform(get("/api/v1/posts/drafts").header("Authorization", token(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].imageKeys[0]").value("image.jpg"));
    }

    private String token(User user) { return "Bearer " + jwt.generateAccessToken(user.getId()); }

    private User user() {
        return users.save(User.builder().username("like-api-" + System.nanoTime()).password("password")
                .name("테스트").ageGroup(AgeGroup.TWENTIES).gender(Gender.MALE).build());
    }

    private Post createPost(User author, PostStatus status) {
        return posts.save(Post.create(author, BoardType.GENERAL, "api-test-" + System.nanoTime(),
                "내용", null, status, List.of("image.jpg")));
    }
}
