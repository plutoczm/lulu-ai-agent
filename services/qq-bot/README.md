# QQ Bot Service

Optional QQ official-bot channel adapter.

This service does not implement its own AI logic. It receives QQ events, normalizes the
conversation, calls the backend channel API, and formats the A/B/C suggestions.

```text
QQ -> services/qq-bot -> /api/channel/coach/suggest -> backend coach
```

Configuration is loaded from the repository-root `.env.local`.

## Commands

```bash
npm ci
npm test
npm start
```
