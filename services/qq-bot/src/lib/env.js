import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const projectRoot = path.resolve(here, "..", "..", "..", "..");

export function loadDotEnv(filePath = path.join(projectRoot, ".env.local")) {
  const result = {};
  if (!fs.existsSync(filePath)) return result;

  for (const raw of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const idx = line.indexOf("=");
    const key = line.slice(0, idx).trim();
    let value = line.slice(idx + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    result[key] = value;
  }
  return result;
}
export function readConfig() {
  const env = { ...loadDotEnv(), ...process.env };

  const bool = (key, fallback = false) => {
    const raw = env[key];
    if (raw == null || raw === "") return fallback;
    return String(raw).toLowerCase() === "true";
  };

  const number = (key, fallback) => {
    const value = Number(env[key]);
    return Number.isFinite(value) ? value : fallback;
  };

  return {
    enabled: bool("QQ_BOT_ENABLED", false),
    appId: env.QQ_BOT_APP_ID || "",
    appSecret: env.QQ_BOT_APP_SECRET || "",
    transport: env.QQ_BOT_TRANSPORT || "websocket",
    webhookPort: number("QQ_BOT_WEBHOOK_PORT", 8132),
    webhookPath: env.QQ_BOT_WEBHOOK_PATH || "/qqbot/webhook",
    healthPort: number("QQ_BOT_HEALTH_PORT", 8131),
    coachApi:
      env.QQ_BOT_COACH_API ||
      "http://127.0.0.1:8123/api/channel/coach/suggest",
    channelToken: env.QQ_BOT_CHANNEL_TOKEN || env.LULU_CHANNEL_TOKEN || "",
    groupCoachEnabled: bool("QQ_BOT_GROUP_COACH_ENABLED", false),
    markdownSupport: bool("QQ_BOT_MARKDOWN", false),
  };
}
