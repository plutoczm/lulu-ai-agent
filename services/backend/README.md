# Backend Service

Spring Boot core API for LULU AI.

## Responsibilities

- Authentication and usage limits
- Conversation coach (A aggressive / B normal / C conservative)
- Super-agent orchestration
- Model routing
- PGVector relationship memory
- Bailian / local RAG
- ChatGPT OAuth and Codex integration
- Tool and MCP integration
- System-status APIs

## Run from repository root

```powershell
.\mvnw.cmd -f services\backend\pom.xml spring-boot:run
```

The normal development entrypoint is `scripts/dev/start-lulu-ai-agent.cmd`.

## Package boundaries

Business logic should remain in feature packages such as `coach`, `agent`, `memory`,
`model`, `rag`, and `tools`. The `controller` package is the HTTP boundary and
should stay thin.

See [`../../docs/architecture/BACKEND_PACKAGES.md`](../../docs/architecture/BACKEND_PACKAGES.md)
for the package map, dependency direction and common change locations.
