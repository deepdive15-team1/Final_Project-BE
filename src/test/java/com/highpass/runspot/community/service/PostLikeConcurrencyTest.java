package com.highpass.runspot.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;
import com.highpass.runspot.community.domain.PostStatus;
import com.highpass.runspot.community.domain.dao.PostRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 동시 좋아요 요청에서 원자적 UPDATE 방식과, 이전에 쓰이던 "엔티티 로드 후 필드 증가" 방식의
 * 실제 결과 차이를 측정한다. 재현 가능한 실패(레거시 방식) → 개선(원자적 UPDATE)을 같은 테스트에서 증명한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PostLikeConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 100;

    @Autowired private PostRepository posts;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    void 원자적_UPDATE_방식은_동시_좋아요_100건을_유실_없이_반영한다() throws InterruptedException {
        Long postId = createPost();

        runConcurrently(
                CONCURRENT_REQUESTS,
                () -> new TransactionTemplate(txManager)
                        .execute(status -> {
                            posts.incrementLikeCount(postId);
                            return null;
                        }));

        int likeCount = reload(postId).getLikeCount();
        System.out.println("[원자적 UPDATE] 요청 " + CONCURRENT_REQUESTS + "건 -> 최종 likeCount = " + likeCount);
        assertThat(likeCount).isEqualTo(CONCURRENT_REQUESTS);
    }

    @Test
    void 엔티티_로드_후_증가_방식은_동시_좋아요_100건에서_카운트를_유실한다() throws InterruptedException {
        Long postId = createPost();

        runConcurrently(CONCURRENT_REQUESTS, () -> new TransactionTemplate(txManager).execute(status -> {
            Post post = posts.findById(postId).orElseThrow();
            sleepToWidenRaceWindow();
            post.increaseLikeCount();
            return null;
        }));

        int likeCount = reload(postId).getLikeCount();
        System.out.println("[엔티티 로드 후 증가] 요청 " + CONCURRENT_REQUESTS + "건 -> 최종 likeCount = " + likeCount
                + " (유실 " + (CONCURRENT_REQUESTS - likeCount) + "건)");
        assertThat(likeCount).isLessThan(CONCURRENT_REQUESTS);
    }

    private void sleepToWidenRaceWindow() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Post reload(Long postId) {
        return new TransactionTemplate(txManager)
                .execute(status -> posts.findById(postId).orElseThrow());
    }

    private Long createPost() {
        return new TransactionTemplate(txManager).execute(status -> {
            User author = users.save(User.builder()
                    .username("concurrency-" + System.nanoTime())
                    .password("password")
                    .name("동시성테스트")
                    .ageGroup(AgeGroup.TWENTIES)
                    .gender(Gender.MALE)
                    .build());
            Post post = Post.create(
                    author, BoardType.GENERAL, "동시성 테스트 게시글", "내용", null, PostStatus.PUBLISHED, List.of());
            return posts.save(post).getId();
        });
    }

    private void runConcurrently(int requestCount, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        for (int i = 0; i < requestCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
