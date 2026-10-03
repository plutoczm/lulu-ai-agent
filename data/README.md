# Data Directory

This directory contains project data, not application source code.

```text
data/
├─ knowledge/
│  └─ relationship-coach/   # Prepared relationship / conversation-coach corpus
├─ auth/
│  └─ chatgpt/              # Local ChatGPT OAuth state (Git-ignored)
├─ model-settings/           # Local model-route preferences (Git-ignored)
└─ channel-runtime/          # Channel runtime state such as QQ profiles (Git-ignored)
```

Rules:
- Knowledge corpus files may be versioned when they are intentional project inputs.
- Credentials, OAuth state, runtime profiles and local preferences must stay Git-ignored.
- Generated build output does not belong in `data/`.
