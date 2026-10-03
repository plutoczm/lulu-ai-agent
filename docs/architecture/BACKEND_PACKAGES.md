# Backend Package Guide

The Spring Boot backend lives in `services/backend/`.

Its package root is:

```text
com.lulu.luluaiagent
├─ agent/        Super Agent workflow and orchestration
├─ auth/         Account, session, authorization and usage limits
├─ coach/        Conversation-coach request/response and A/B/C generation
├─ controller/   HTTP API boundary; keep controllers thin
├─ memory/       User-scoped relationship memory backed by PGVector
├─ model/        Model routing and provider integrations
│  ├─ chatgpt/   ChatGPT OAuth / Codex integration
│  └─ runtime/   Provider registry and runtime routing model
├─ rag/          Relationship knowledge retrieval and vector-store config
├─ tools/        Deterministic tools available to Super Agent
├─ config/       Framework and repository-path configuration
├─ advisor/      Reusable Spring AI advisors
└─ constant/     Small shared constants
```

## Dependency direction

Use this direction when adding code:

```text
controller
   ↓
feature service (coach / agent / memory / model)
   ↓
integration layer (rag / tools / provider clients)
   ↓
external systems
```

Controllers should not contain product decisions, prompt construction, database logic or provider-specific code.

## Where common changes belong

| Change | Location |
| --- | --- |
| Change A/B/C reply policy | `coach/ConversationCoachService.java` |
| Change coach API contract | `coach/*Request.java`, `coach/*Response.java` |
| Add/modify public API endpoint | `controller/` (for example `ModelController`, `SuperAgentController`) |
| Change login/session behavior | `auth/` |
| Add a model/provider or routing rule | `model/` |
| Change ChatGPT/Codex integration | `model/chatgpt/` |
| Change long-term relationship memory | `memory/` |
| Change Bailian/local knowledge retrieval | `rag/` |
| Add a deterministic Agent tool | `tools/` |
| Change repository/runtime paths | `config/ProjectPaths.java` |

## Resource layout

```text
services/backend/src/main/resources/
├─ application.yml
├─ application-local.yml
├─ application-prod.yml
├─ mcp-servers.json
└─ knowledge/
   └─ relationship/       Packaged local-fallback Markdown knowledge
```

The larger prepared corpus used for synchronization with Bailian belongs under
`data/knowledge/relationship-coach/`, not inside the backend source tree.

## Primary request flows

### Web / Android coach

```text
HTTP request
  → ConversationCoachController
  → ConversationCoachService
  → ModelRouter + optional RAG + optional relationship memory
  → structured A/B/C response
```

### QQ bot

```text
QQ event
  → services/qq-bot
  → ChannelCoachController
  → ConversationCoachService
  → structured A/B/C response
```

### Super Agent

```text
Web request
  → SuperAgentController
  → SuperAgentService
  → deterministic tools / MCP / selected model
```
