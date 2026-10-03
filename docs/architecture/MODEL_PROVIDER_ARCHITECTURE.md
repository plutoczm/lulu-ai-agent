# LULU AI 模型接入架构

参考：

- earendil-works/pi
- packages/ai/README.md

本项目不直接引入 pi-ai 作为 Java 后端运行时，而是借鉴其 Provider / Model Catalog / Routing 架构。

## 设计目标

业务层不应该知道某个模型如何鉴权、使用什么协议或由哪个 SDK 实现。

业务只声明“我要 coach / agent / fast / memory 模型”，由模型注册表解析实际 Provider 与模型。

当前链路：

```text
Business Service
      ↓
ModelRouter
      ↓
ModelRegistry
      ↓
Provider
      ↓
Spring AI ChatModel
```
## 当前 Provider

- deepseek
  - API：OpenAI-compatible
  - 当前模型：由 DEEPSEEK_MODEL 配置
  - 用途：coach / agent 主模型

- dashscope
  - API：Spring AI Alibaba DashScope
  - 当前模型：qwen-plus
  - 用途：fast 与 DeepSeek fallback

- ollama
  - API：本地 Ollama
  - 当前模型：qwen3:8b
  - 用途：memory
  - local=true

## 模型能力

当前统一能力标签：

- CHAT
- STREAMING
- TOOLS
- STRUCTURED_OUTPUT
- LOCAL
## 路由

默认：

```text
coach  : deepseek → dashscope
agent  : deepseek → dashscope
fast   : dashscope → deepseek
memory : ollama
```

可通过环境变量覆盖：

```env
MODEL_ROUTE_COACH=deepseek,dashscope
MODEL_ROUTE_AGENT=deepseek,dashscope
MODEL_ROUTE_FAST=dashscope,deepseek
MODEL_ROUTE_MEMORY=ollama
```

路由项也支持精确到模型：

```text
provider/model-id
```

例如后续同一个 Provider 注册多个模型后，可以配置：

```env
MODEL_ROUTE_AGENT=openrouter/anthropic/claude-sonnet-4.5,deepseek
```
## 与 pi-ai 对齐的原则

1. Provider 持有自己的模型目录与运行实现。
2. 模型由 providerId + modelId 唯一定位。
3. 业务不直接注入具体 Provider 的 ChatModel。
4. 路由负责 primary / fallback，不把 fallback 写进业务 Prompt。
5. 模型目录暴露能力元数据，前端可据此展示。
6. 密钥只从环境配置解析，不进入前端。
7. 后续 Provider 应优先复用已有 wire protocol，而不是复制业务逻辑。

## 下一阶段

计划继续补齐：

- Provider 可用性主动探测；
- request timeout / retry 策略；
- token usage 统一统计；
- 成本统计；
- reasoning/thinking 能力元数据；
- image input 能力；
- OpenRouter / OpenAI / Anthropic / Gemini 等 Provider；
- 调用级模型覆盖与跨 Provider handoff。
