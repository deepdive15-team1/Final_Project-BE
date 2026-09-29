import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createCommunityApi } from './community-api.mjs';

test('draft array, singular like route, HTTP methods, auth and empty 204', async () => {
  const calls = [];
  const api = createCommunityApi({
    apiOrigin: 'https://example.com', getAccessToken: () => 'token',
    fetchImpl: async (url, options) => {
      calls.push([url, options]);
      return options.method === 'GET'
        ? new Response('[{"postId":7}]', { status: 200 })
        : new Response(null, { status: 204 });
    },
  });
  assert.deepEqual(await api.getDrafts(), [{ postId: 7 }]);
  assert.equal(await api.like(7), undefined);
  assert.equal(await api.unlike(7), undefined);
  assert.deepEqual(calls.map(([url, options]) => [url, options.method]), [
    ['https://example.com/api/v1/posts/drafts', 'GET'],
    ['https://example.com/api/v1/posts/7/like', 'POST'],
    ['https://example.com/api/v1/posts/7/like', 'DELETE'],
  ]);
  assert.ok(calls.every(([, options]) => options.headers.Authorization === 'Bearer token'));
  assert.throws(() => api.like(undefined), /postId/);
});

test('server 500 and 404 remain errors instead of empty lists or success', async () => {
  for (const status of [404, 500]) {
    const api = createCommunityApi({
      apiOrigin: 'https://example.com', getAccessToken: () => 'token',
      fetchImpl: async () => new Response(JSON.stringify({ message: '서버 오류' }), { status }),
    });
    await assert.rejects(api.getDrafts(), { status, message: '서버 오류' });
    await assert.rejects(api.like(7), { status, message: '서버 오류' });
  }
});
