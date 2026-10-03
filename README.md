# LULU AI

**噜噜（LULU AI）** 是一个本地优先的个人 AI 助手项目，采用单仓库多模块（monorepo）结构。

当前产品包含：
- **Web 对话军师**：粘贴真实对话，生成 A｜激进、B｜正常、C｜保守三条参考回复。
- **Android 手机聊天助手**：同时覆盖微信和 QQ，悬浮显示 A/B/C，点击只复制，不自动发送。
- **AI 超级智能体**：搜索、网页、文件、工具调用与多模型任务处理。
- **QQ 官方机器人**：可选的 QQ 渠道适配，复用同一个对话军师核心。

## Architecture

```text
lulu-ai-agent/
├─ apps/
│  ├─ web/                     # Vue 3 + Vite Web client
│  └─ android/                 # Kotlin Android client (WeChat + QQ)
├─ services/
│  ├─ backend/                 # Spring Boot core API
│  ├─ qq-bot/                  # Optional QQ official-bot adapter
│  └─ image-search-mcp/        # Optional image-search MCP service
├─ infra/
│  └─ compose.public.yml       # Public Docker Compose stack
├─ docs/
│  ├─ architecture/            # System / model architecture
│  ├─ product/                 # Product specs and brand
│  └─ channels/                # Android / QQ channel designs
├─ scripts/
│  ├─ dev/                     # Start / stop / status / public demo
│  └─ data/                    # Knowledge corpus maintenance
├─ data/                       # Runtime state + prepared knowledge corpus
├─ tools/                      # Local third-party binaries
├─ .env.example
├─ pom.xml                     # Maven reactor aggregator
├─ mvnw / mvnw.cmd
└─ README.md
```

Detailed architecture: [docs/architecture/OVERVIEW.md](docs/architecture/OVERVIEW.md)

## Quick orientation

| If you want to change... | Go to |
| --- | --- |
| Web pages and interactions | `apps/web/` |
| Android WeChat / QQ overlay | `apps/android/` |
| A/B/C conversation strategy | `services/backend/.../coach/` |
| Super Agent workflow | `services/backend/.../agent/` |
| Models / ChatGPT / Codex routing | `services/backend/.../model/` |
| Relationship memory | `services/backend/.../memory/` |
| RAG / knowledge retrieval | `services/backend/.../rag/` |
| QQ official-bot transport | `services/qq-bot/` |
| Image-search MCP | `services/image-search-mcp/` |
| Local start / stop / status | `scripts/dev/` |
| Deployment definitions | `infra/` |

Backend package details: [docs/architecture/BACKEND_PACKAGES.md](docs/architecture/BACKEND_PACKAGES.md)

## Main runtime flow

```text
apps/web ───────────────┐
                       │
apps/android ───────────┼──> services/backend
                       │       ├─ Conversation Coach
services/qq-bot ────────┘       ├─ Super Agent
                               ├─ Model Router
                               ├─ PGVector Memory
                               ├─ RAG
                               └─ Tools / MCP
```

The backend is the single source of business logic. Web, Android and QQ Bot are clients/adapters.

## Tech stack

**Backend**
- Java 21 / Spring Boot
- Spring AI / Spring AI Alibaba
- PostgreSQL + PGVector
- DeepSeek / Qwen / Ollama
- ChatGPT OAuth + Codex app-server
- MCP

**Web**
- Vue 3
- Vite
- Axios

**Android**
- Kotlin
- Android overlay
- ACTION_SEND / PROCESS_TEXT
- System clipboard

**Channel / tools**
- Tencent QQ Bot SDK
- Image Search MCP
- Cloudflare Tunnel

## Local development

Copy the environment template first:

```text
.env.example -> .env.local
```

Recommended Windows entrypoints:

```text
scripts/dev/start-lulu-ai-agent.cmd
scripts/dev/status-lulu-ai-agent.cmd
scripts/dev/stop-lulu-ai-agent.cmd
```

Default local endpoints:
- Web: `http://127.0.0.1:3000/`
- Backend: `http://127.0.0.1:8123/api`
- QQ Bot health: `http://127.0.0.1:8131/health` (when enabled)

## Build

Build all Maven services from repository root:

```powershell
.\mvnw.cmd -DskipTests package
```

Build only the backend:

```powershell
.\mvnw.cmd -f services\backend\pom.xml -DskipTests package
```

Build the Web client:

```powershell
cd apps\web
npm ci
npm run build
```

## Documentation

See [docs/README.md](docs/README.md).

The most useful entrypoints are:
- [Architecture overview](docs/architecture/OVERVIEW.md)
- [Conversation coach product spec](docs/product/CHAT_COPILOT_V1.md)
- [Android mobile assistant](docs/channels/ANDROID_CHAT_ASSISTANT.md)
- [Model provider architecture](docs/architecture/MODEL_PROVIDER_ARCHITECTURE.md)

## Repository conventions

- Product UI belongs in `apps/`.
- Independently runnable backend/channel/tool processes belong in `services/`.
- Deployment files belong in `infra/`.
- Repeatable operational tasks belong in `scripts/`.
- Runtime state and prepared corpora belong in `data/`.
- Generated output (`target/`, `dist/`, logs, caches) is not source code and is Git-ignored.
- Secrets are never committed; use `.env.local` / `.env.prod`.

## Upstream

The project originally evolved from the `liyupi/yu-ai-agent` learning project and has since
been substantially reworked around LULU AI product flows, model routing, memory, RAG,
ChatGPT/Codex integration, Android/QQ channels and a new Web UI.
