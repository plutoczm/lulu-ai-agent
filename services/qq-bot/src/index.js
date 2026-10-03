import {
  QQBot,
  messageFilter,
  contentSanitizer,
  concurrencyGuard,
} from "@tencent-connect/qqbot-nodejs";

import { readConfig } from "./lib/env.js";
import { ProfileStore } from "./lib/profile-store.js";
import { HELP_TEXT, parseConversation } from "./lib/parser.js";
import { handleCommand } from "./lib/commands.js";
import {
  formatCoachResult,
  formatErrorMessage,
} from "./lib/formatter.js";
import {
  buildCoachPayload,
  requestCoach,
} from "./lib/coach-client.js";
import { startHealthServer } from "./lib/health.js";

const config = readConfig();
const store = new ProfileStore();
const state = {
  ready: false,
  lastMessageAt: null,
  lastError: null,
};
const healthServer = startHealthServer(config, state);

if (!config.enabled) {
  console.log("[lulu-qq] QQ_BOT_ENABLED=false; adapter is idle.");
  console.log(
    "[lulu-qq] Health: http://127.0.0.1:" +
      config.healthPort +
      "/health",
  );
  process.on("SIGINT", () => {
    healthServer.close();
    process.exit(0);
  });
  process.on("SIGTERM", () => {
    healthServer.close();
    process.exit(0);
  });
} else if (!config.appId || !config.appSecret) {
  console.error(
    "[lulu-qq] Missing QQ_BOT_APP_ID / QQ_BOT_APP_SECRET.",
  );
  process.exitCode = 2;
  healthServer.close();
} else {
  await startBot();
}
async function startBot() {
  const options = {
    appId: config.appId,
    appSecret: config.appSecret,
    accountId: "lulu",
    markdownSupport: config.markdownSupport,
    tokenPrefetch: "sync",
    transport: config.transport,
    logger: createLogger(),
  };

  if (config.transport === "webhook") {
    options.webhook = {
      port: config.webhookPort,
      path: config.webhookPath,
    };
  }

  const bot = new QQBot(options);

  bot.use(
    messageFilter({
      skipSelfEcho: true,
      dedup: { windowMs: 10_000, maxSize: 2000 },
    }),
  );
  bot.use(
    contentSanitizer({
      stripBotMention: true,
      stripAllMentions: false,
      collapseWhitespace: false,
    }),
  );

  bot.use(
    concurrencyGuard({
      strategy: "queue",
      maxQueue: 3,
      maxProcessingMs: 120_000,
    }),
  );

  bot.on("ready", () => {
    state.ready = true;
    state.lastError = null;
    console.log("[lulu-qq] connected");
  });

  bot.on("resumed", () => {
    state.ready = true;
    state.lastError = null;
    console.log("[lulu-qq] resumed");
  });
  bot.on("error", (error) => {
    state.lastError = safeError(error);
    console.error("[lulu-qq] sdk error:", state.lastError);
  });

  bot.on("message", async (_ctx, msg) => {
    state.lastMessageAt = new Date().toISOString();
    await handleMessage(bot, msg);
  });

  const shutdown = () => {
    state.ready = false;
    bot.stop();
    healthServer.close();
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);

  state.ready = config.transport === "webhook";
  console.log(
    "[lulu-qq] starting transport=" + config.transport +
      " health=127.0.0.1:" + config.healthPort,
  );

  await bot.start();
}
async function handleMessage(bot, msg) {
  const userId = msg.senderId;
  const text = String(msg.content || "").trim();

  if (msg.kind === "group" && !config.groupCoachEnabled) {
    await bot.sendText(
      msg.replyTarget,
      "为了保护聊天隐私，请私聊噜噜获取回复建议。发送 /帮助 查看用法。",
    );
    return;
  }

  if (!text) {
    if (msg.attachments?.length) {
      await bot.sendText(
        msg.replyTarget,
        "我收到附件了。目前 QQ 版先支持文字聊天片段；请把关键几句复制成文字发给我。",
      );
    }
    return;
  }

  if (text.length > 6000) {
    await bot.sendText(
      msg.replyTarget,
      "这段内容有点长。请只保留和当前问题最相关的最近几句聊天，再发给我。",
    );
    return;
  }
  if (["你好", "嗨", "hello", "hi"].includes(text.toLowerCase())) {
    await bot.sendText(
      msg.replyTarget,
      "你好，我是噜噜。把最近几句微信或 QQ 对话发给我，我会先给一条能直接发送的回复。\n\n" +
        HELP_TEXT,
    );
    return;
  }

  const commandReply = handleCommand(text, userId, store);
  if (commandReply) {
    await bot.sendText(msg.replyTarget, commandReply);
    return;
  }

  const profile = store.get(userId);
  const messages = parseConversation(text, profile.otherAlias);
  if (!messages.length) {
    await bot.sendText(msg.replyTarget, HELP_TEXT);
    return;
  }

  if (msg.replyTarget.scope === "c2c") {
    try {
      await bot.sendTyping(msg.replyTarget, 30);
    } catch {
      // typing is optional
    }
  }
  try {
    const payload = buildCoachPayload({
      platform: "qq",
      senderId: userId,
      profile,
      messages,
    });
    const result = await requestCoach(config, payload);
    await bot.sendText(msg.replyTarget, formatCoachResult(result));
  } catch (error) {
    state.lastError = safeError(error);
    console.error("[lulu-qq] message failed:", state.lastError);
    await bot.sendText(msg.replyTarget, formatErrorMessage(error));
  }
}

function safeError(error) {
  const text = String(error?.message || error || "unknown");
  return text
    .replace(config.appSecret, "[redacted]")
    .replace(config.appId, "[app-id]");
}

function createLogger() {
  return {
    info: (msg) => console.log("[qq-sdk]", msg),
    warn: (msg) => console.warn("[qq-sdk]", msg),
    error: (msg) => console.error("[qq-sdk]", msg),
    debug: () => {},
  };
}
