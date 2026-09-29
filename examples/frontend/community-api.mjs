// apiOrigin에는 /api/v1을 제외한 백엔드 origin을 전달합니다.
export function createCommunityApi({ apiOrigin, getAccessToken, fetchImpl = globalThis.fetch }) {
  const origin = new URL(apiOrigin).origin;

  async function request(path, method = 'GET') {
    const token = await getAccessToken();
    if (!token) throw new Error('로그인이 필요합니다.');
    const response = await fetchImpl(`${origin}/api/v1${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    });
    if (response.status === 204) return;
    const text = await response.text();
    let body;
    try { body = text ? JSON.parse(text) : null; } catch { body = null; }
    if (!response.ok) {
      const error = new Error(body?.message || `API 요청 실패 (${response.status})`);
      error.status = response.status;
      error.path = path;
      throw error;
    }
    return body;
  }

  function postPath(postId) {
    if (!/^[1-9]\d*$/.test(String(postId))) {
      throw new Error('응답의 유효한 postId가 필요합니다.');
    }
    return `/posts/${postId}`;
  }

  return {
    // 응답 자체가 배열입니다. response.items나 response.data로 접근하지 않습니다.
    getDrafts: () => request('/posts/drafts'),
    getPost: (postId) => request(postPath(postId)),
    like: (postId) => request(`${postPath(postId)}/like`, 'POST'),
    unlike: (postId) => request(`${postPath(postId)}/like`, 'DELETE'),
  };
}
