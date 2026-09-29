# 커뮤니티 프론트 적용

`community-api.mjs`를 프론트 프로젝트에 복사해 사용합니다. 추가 패키지는 필요 없습니다.

```js
import { createCommunityApi } from './community-api.mjs';

const communityApi = createCommunityApi({
  apiOrigin: 'https://YOUR_BACKEND_HOST',
  getAccessToken: () => authStore.accessToken, // 프로젝트의 현재 토큰 조회로 교체
});

const drafts = await communityApi.getDrafts(); // 배열, 빈 목록은 []
const post = await communityApi.getPost(postId);
await communityApi.like(post.postId); // 성공 204, JSON 파싱하지 않음
await communityApi.unlike(post.postId); // 성공 204
```

- 좋아요 등록은 `POST /api/v1/posts/{postId}/like`, 취소는 같은 URL의 `DELETE`입니다. `likes`, `community` 경로가 아닙니다.
- 게시글 응답의 `postId`를 사용합니다. 코스 ID나 러닝 기록 ID를 전달하지 않습니다.
- 목록 응답에는 `liked`가 없습니다. `likeCount > 0`으로 내 좋아요 여부를 판단하면 안 됩니다. 상세 응답의 `liked`로 버튼을 초기화하고, 처리 중 버튼을 비활성화해 중복 요청을 막습니다. 목록에서 토글하려면 먼저 상세 조회로 내 상태를 확인합니다.
- 좋아요 성공 후 상세를 다시 조회해 `liked`와 `likeCount`를 갱신합니다. 409나 취소 시 404가 발생해도 성공으로 간주하지 말고 상세를 다시 조회해 상태를 맞춥니다.
- 404의 응답 메시지를 확인합니다. `게시글을 찾을 수 없습니다.`는 게시글 부재 또는 미발행 상태, `좋아요 내역을 찾을 수 없습니다.`는 취소할 내역 부재입니다. HTML 404나 다른 메시지는 요청 주소·프록시·배포 버전을 확인합니다.
- 임시저장 500은 프론트에서 빈 배열로 숨기지 않습니다. 이 저장소의 조회 수정이 백엔드에 배포되어야 합니다. 현재 운영 오류와 동일한 원인인지는 서버 로그로 확인해야 합니다.
- 이 코드는 전달용 모듈이며 실제 프론트 저장소에는 아직 연결되지 않았습니다.

검증: `node --test examples/frontend/community-api.test.mjs`
