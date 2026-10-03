export const HELP_TEXT = [
  "噜噜 QQ 对话军师",
  "",
  "把最近几句聊天直接发给我即可，例如：",
  "我：今天还顺利吗",
  "对方：累死了，今天开了一天会",
  "",
  "可用命令：",
  "/对象 小林",
  "/阶段 暧昧期",
  "/风格 短句自然，不油腻",
  "/目标 自动判断",
  "/状态",
  "/清空",
  "/帮助",
].join("\n");

export function parseCommand(text) {
  const raw = String(text || "").trim();
  if (!raw.startsWith("/")) return null;

  const firstSpace = raw.search(/\s/);
  const name = (firstSpace === -1 ? raw : raw.slice(0, firstSpace))
    .replace(/^\//, "")
    .trim();
  const value = firstSpace === -1 ? "" : raw.slice(firstSpace).trim();
  return { name, value };
}
export function parseConversation(text, otherAlias = "对方") {
  const lines = String(text || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  if (!lines.length) return [];

  return lines.map((line, index) => {
    const match = line.match(/^([^：:]{1,20})[：:]\s*(.+)$/);
    if (!match) {
      return {
        sender: otherAlias || "对方",
        text: line,
        time: String(index + 1),
      };
    }

    const rawSender = match[1].trim();
    const sender =
      rawSender === "我"
        ? "我"
        : rawSender === "对方"
          ? (otherAlias || "对方")
          : rawSender;

    return {
      sender,
      text: match[2].trim(),
      time: String(index + 1),
    };
  });
}
