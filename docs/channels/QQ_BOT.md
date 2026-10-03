# QQ Official Bot Channel

## Positioning

The QQ official bot is an **optional channel adapter**.

It is not the primary QQ mobile solution. The primary mobile experience is the shared Android assistant in `apps/android/`, which works over both WeChat and QQ.

The bot reuses the same backend conversation-coach logic:

```text
QQ user
  → services/qq-bot
  → POST /api/channel/coach/suggest
  → ConversationCoachService
  → A / B / C suggestions
  → QQ bot reply
```

The adapter must not duplicate prompt strategy, model routing, RAG or memory logic.

## Configuration

Credentials live in the repository-root `.env.local` and must never be committed.

```env
QQ_BOT_ENABLED=true
QQ_BOT_APP_ID=
QQ_BOT_APP_SECRET=
QQ_BOT_TRANSPORT=websocket
QQ_BOT_HEALTH_PORT=8131
QQ_BOT_GROUP_COACH_ENABLED=false
QQ_BOT_MARKDOWN=false
QQ_BOT_CHANNEL_TOKEN=
```

The backend must use the same channel token through `LULU_CHANNEL_TOKEN`.

## Local development

Use the normal repository entrypoint:

```text
scripts/dev/start-lulu-ai-agent.cmd
```

Health endpoint:

```text
http://127.0.0.1:8131/health
```

Runtime logs:

```text
.run/logs/qqbot.out.log
.run/logs/qqbot.err.log
```

## Conversation behavior

For a coach request, the bot formats exactly three reference replies:

- A | aggressive
- B | normal
- C | conservative

The bot does not send messages to a third party on the user's behalf.

Group coaching is disabled by default. Enable it only when explicitly needed:

```env
QQ_BOT_GROUP_COACH_ENABLED=true
```

## Transport

Local development uses WebSocket by default:

```env
QQ_BOT_TRANSPORT=websocket
```

Webhook deployment remains optional and requires a public HTTPS callback.

## Related documentation

- Android mobile assistant: [ANDROID_CHAT_ASSISTANT.md](ANDROID_CHAT_ASSISTANT.md)
- Architecture overview: [../architecture/OVERVIEW.md](../architecture/OVERVIEW.md)
