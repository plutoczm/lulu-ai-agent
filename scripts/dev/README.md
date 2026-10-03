# Windows 本地开发启停脚本

本目录保存 lulu-ai-agent 的本地开发运行入口，桌面只保留快捷方式。

## 双击入口

桌面保留中文快捷方式，实际项目入口统一使用 ASCII 文件名和 Windows CRLF 换行，避免 cmd.exe 编码兼容问题：

- `start-lulu-ai-agent.cmd`：检查并启动 PGVector、Ollama、Spring Boot、Vite；如果 QQ Bot 已启用，也会启动 QQ 官方机器人，然后打开前端。
- `stop-lulu-ai-agent.cmd`：停止前端、后端和 QQ Bot，保留 PGVector / Ollama 以便快速再次启动。
- `stop-all-lulu-ai-agent.cmd`：进一步停止项目基础设施。
- `status-lulu-ai-agent.cmd`：查看数据库、本地模型、前端、后端和 QQ Bot 状态。

## 实际逻辑

CMD 只负责双击入口，主要逻辑在：

- `start-dev.ps1`
- `stop-dev.ps1`
- `status-dev.ps1`

运行时 PID 和日志写入项目根目录的 `.run/`，该目录已被 Git 忽略。
