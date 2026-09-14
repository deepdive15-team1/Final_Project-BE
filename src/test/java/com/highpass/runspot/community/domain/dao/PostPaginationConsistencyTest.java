package com.highpass.runspot.community.domain.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.community.domain.BoardType;
import com.highpass.runspot.community.domain.Post;
import com.highpass.runspot.community.domain.PostStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 무한스크롤 목록에서 offset 페이징이 실시간 삽입 시 중복을 일으키는 것과, id 커서 페이징이
 * 같은 상황에서 중복 없이 동작하는 것을 같은 데이터셋으로 비교 측정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PostPaginationConsistencyTest {

    private static final int PAGE_SIZE = 10;
    private static final int INITIAL_POST_COUNT = 20;

    @Autowired private PostRepository posts;
    @Autowired private UserRepository users;

    @Test
    void 오프셋_페이징은_새_글_삽입_후_2페이지에서_1페이지와_중복된_글이_나타난다() {
        User author = createAuthor();
        createPosts(author, INITIAL_POST_COUNT);

        Page<Post> page1 = posts.findAll(PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));

        // 사용자가 1페이지를 보는 사이 새 글이 1건 올라옴
        createPosts(author, 1);

        Page<Post> page2 = posts.findAll(PageRequest.of(1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));

        Set<Long> duplicates = intersect(page1.getContent(), page2.getContent());
        System.out.println("[offset 페이징] 새 글 1건 삽입 후 2페이지 조회 -> 1페이지와 중복된 게시글 "
                + duplicates.size() + "건: " + duplicates);
        assertThat(duplicates).isNotEmpty();
    }

    @Test
    void 커서_페이징은_새_글_삽입_후에도_중복이_발생하지_않는다() {
        User author = createAuthor();
        createPosts(author, INITIAL_POST_COUNT);

        Slice<Post> page1 = posts.findLatest(null, null, null, PageRequest.of(0, PAGE_SIZE));
        Long cursor = page1.getContent().get(page1.getContent().size() - 1).getId();

        // 사용자가 1페이지를 보는 사이 새 글이 1건 올라옴
        createPosts(author, 1);

        Slice<Post> page2 = posts.findLatest(null, null, cursor, PageRequest.of(0, PAGE_SIZE));

        Set<Long> duplicates = intersect(page1.getContent(), page2.getContent());
        System.out.println("[cursor 페이징] 새 글 1건 삽입 후 2페이지 조회 -> 중복 " + duplicates.size() + "건");
        assertThat(duplicates).isEmpty();
    }

    private Set<Long> intersect(List<Post> a, List<Post> b) {
        Set<Long> idsA = new HashSet<>(a.stream().map(Post::getId).toList());
        Set<Long> idsB = new HashSet<>(b.stream().map(Post::getId).toList());
        idsA.retainAll(idsB);
        return idsA;
    }

    private void createPosts(User author, int count) {
        for (int i = 0; i < count; i++) {
            posts.save(Post.create(
                    author, BoardType.GENERAL, "게시글", "내용", null, PostStatus.PUBLISHED, List.of()));
        }
    }

    private User createAuthor() {
        return users.save(User.builder()
                .username("pagination-" + System.nanoTime())
                .password("password")
                .name("페이지네이션테스트")
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build());
    }
}
