# LULU AI Web

Vue 3 + Vite browser client for LULU AI.

## Responsibilities

- Web conversation coach: paste recent messages and show A / B / C reply suggestions.
- Super Agent UI.
- Model settings and system-status pages.
- Authentication UI.

The Web app does **not** distinguish WeChat from QQ. Chat source is treated as generic Web input and sent with `platform=web`.

Mobile WeChat / QQ interaction belongs to `apps/android/`.

## Development

```bash
npm ci
npm run dev
npm run build
```

Default URL: `http://127.0.0.1:3000/`

Backend API base: `/api`

Primary coach endpoint:

```text
POST /api/ai/coach/suggest
```

Generated suggestions are references only. Clicking a suggestion copies it to the clipboard; the Web app never sends a third-party chat message.
