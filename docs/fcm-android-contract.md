# Android FCM and Server Operations Contract

## Scope and delivery guarantee

This contract supports Android only. iOS and APNs are unsupported.

The server stores one Android token per user and sends through an outbox with at-least-once delivery semantics. A message can be delivered more than once after a lease expiry or retry. Clients must not assume exactly-once delivery. A send already issued to FCM cannot be recalled by token revocation, logout, or account withdrawal.

## Authenticated token lifecycle API

Every push-token request requires an access token in `Authorization: Bearer <access-token>`. Unauthenticated requests receive the established JSON 401 response:

```json
{"status":401,"message":"로그인이 필요합니다."}
```

### Register or replace a token

```http
PUT /users/me/push-token
Authorization: Bearer <access-token>
Content-Type: application/json

{"token":"<fcm-registration-token>","platform":"ANDROID"}
```

Success is `204 No Content` with no response body. `token` must be non-blank and at most 512 characters. `platform` must be exactly `ANDROID`; iOS is rejected with `400 Bad Request`.

The operation is idempotent when the same authenticated user registers the same token: it is a successful no-op. Registering a different token replaces that user's prior token. If the same token is currently owned by another user, the server transfers ownership to the caller. Concurrent registration conflicts can return `409 Conflict`.

### Revoke a token

```http
DELETE /users/me/push-token
Authorization: Bearer <access-token>
```

Success is `204 No Content` with no response body, including when no token is currently stored. Revoke before discarding the local access token. It removes the local token and unsent `PENDING` or `PROCESSING` delivery work; it cannot recall an external send that FCM has already received.

### Login, logout, and withdrawal

After authenticated login, upload the current FCM token. Refresh the registration whenever Firebase produces a new token. Logout uses its existing refresh-token request and does not require the access token:

```http
POST /auth/logout
Content-Type: application/json

{"refreshToken":"<refresh-token>"}
```

When that refresh token is stored, the server removes the associated token and unsent push work before invalidating the refresh token. If the refresh token is unknown, logout remains successful and does not infer a user for cleanup.

`DELETE /auth/withdraw` requires the current access token and removes the user's token and related push state as part of account deletion. The client should revoke before clearing local credentials where possible, but server-side logout and withdrawal cleanup remains the authority for their completed requests.

## Android Firebase lifecycle

Implement `FirebaseMessagingService.onNewToken(token)` and send the token through the registration API after the user is authenticated. Firebase calls this after first installation and again when the registration token changes. Also obtain and register the current token after login if `onNewToken` did not run during that session.

The server sends an Android notification plus data payload at high priority. Create the Android notification channel with this exact ID:

```text
runspot_notifications
```

Declare an Activity intent filter for this exact click action and route the launch intent to the matching notification screen:

```text
RUNSPOT_NOTIFICATION_CLICK
```

When the app is foregrounded, `onMessageReceived` receives this notification payload; process its data and show the appropriate in-app or local notification UI. When the app is backgrounded, Android places this notification-plus-data payload in the system tray; extract the data keys from the launch Activity intent extras after the user taps it. Do not depend on a background `onMessageReceived` callback for this payload shape.

## FCM payload data

All values are strings. The server sends exactly these six data-key names:

| Key | Value | Presence |
|---|---|---|
| `notificationId` | Notification ID | Always |
| `type` | `PARTICIPATION_REQUESTED`, `PARTICIPATION_APPROVED`, `PARTICIPATION_REJECTED`, `PARTICIPANT_KICKED`, or `SESSION_START_REMINDER` | Always |
| `sessionId` | Session ID | Always |
| `participationId` | Participation ID | Only when non-null; omitted otherwise |
| `actionType` | `APPROVE_OR_REJECT` or `NAVIGATE` | Always |
| `actionStatus` | `PENDING`, `RESOLVED`, or `NONE` | Always |

Treat a missing `participationId` as absent, not as the string `"null"`. Use `notificationId` as the stable deduplication key: persist or compare it before presenting navigation or side effects so at-least-once delivery does not create duplicate user-visible work.
