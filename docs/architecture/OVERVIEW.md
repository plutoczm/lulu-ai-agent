# LULU AI Architecture Overview

## 1. Repository shape

LULU AI is organized as a monorepo with clear runtime boundaries:

```text
lulu-ai-agent/
├─ apps/                    # User-facing clients
│  ├─ web/                  # Vue 3 web application
│  └─ android/              # Android assistant for WeChat / QQ
├─ services/                # Independently runnable services
│  ├─ backend/              # Spring Boot core API
│  ├─ qq-bot/               # Optional QQ official-bot adapter
│  └─ image-search-mcp/     # Image-search MCP server
├─ infra/                   # Deployment and container definitions
├─ docs/                    # Architecture / product / channel docs
├─ scripts/                 # Development and data-maintenance scripts
├─ data/                    # Runtime state and vendored knowledge corpus
├─ tools/                   # Local third-party executables
├─ .env.example             # Environment variable template
├─ pom.xml                  # Maven reactor / monorepo aggregator
└─ README.md
```

## 2. Runtime architecture

```text
                   ┌───────────────────┐
                   │   apps/web        │
                   │ Vue 3 / Vite      │
                   └─────────┬─────────┘
                             │ HTTP / SSE
                             │
┌───────────────────┐        ▼
│ apps/android      │  ┌──────────────────────┐
│ WeChat + QQ       ├─▶│ services/backend     │
│ overlay + copy    │  │ Spring Boot core API │
└───────────────────┘  └──────┬───────┬───────┘
                              │       │
                              │       ├─ PostgreSQL + PGVector
                              │       ├─ Bailian RAG / local RAG
                              │       ├─ DeepSeek / Qwen / Ollama
                              │       └─ Codex / MCP tools
                              │
                     ┌────────▼────────┐
                     │ services/qq-bot │
                     │ optional channel│
                     └─────────────────┘

services/image-search-mcp is spawned by the backend only when MCP is enabled.
```

## 3. Dependency rules

- `apps/*` may call `services/backend`; they do not access databases directly.
- `services/qq-bot` is a channel adapter. Business logic stays in the backend.
- `services/image-search-mcp` exposes a tool capability; it does not own product state.
- `data/` contains runtime state or prepared knowledge. Source code must not be stored there.
- `infra/` contains deployment definitions only.
- `scripts/` contains repeatable operational tasks only; product logic belongs in apps/services.

## 4. Backend package map

The backend keeps a feature-oriented package layout:

- `agent/`: super-agent workflow and orchestration.
- `auth/`: account/session authentication and usage limits.
- `coach/`: A/B/C conversation-coach domain logic.
- `memory/`: relationship long-term memory.
- `model/`: model providers, routes, ChatGPT/Codex integration.
- `rag/`: retrieval and knowledge-base integration.
- `tools/`: deterministic tools exposed to the agent.
- `controller/`: HTTP boundary only.
- `config/`: framework configuration.
- `advisor/`: reusable Spring AI advisors.

Controllers should delegate; product behavior should stay in domain/service packages.

See [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md) for the detailed package map and common change locations.

## 5. Primary product flows

### Web conversation coach

`apps/web -> POST /api/ai/coach/suggest -> ConversationCoachService -> A/B/C -> user copies reply`

### Android mobile assistant

`WeChat/QQ -> user copies/shares text -> apps/android -> backend coach -> overlay A/B/C -> user taps to copy`

### Optional QQ bot

`QQ official bot -> services/qq-bot -> /api/channel/coach/suggest -> same ConversationCoachService`

The QQ bot is an adapter, not a separate AI implementation.

## 6. Local development

Use the scripts under `scripts/dev/`:

- `start-lulu-ai-agent.cmd`: start backend, web, QQ bot and required local infrastructure.
- `status-lulu-ai-agent.cmd`: show service health.
- `stop-lulu-ai-agent.cmd`: stop application services.
- `start-public-demo.cmd`: start the temporary public demo tunnel.

The root Maven wrapper is shared by Maven services. `pom.xml` is a reactor aggregator.
