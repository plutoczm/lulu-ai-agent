import { HELP_TEXT, parseCommand } from "./parser.js";
import { formatProfile } from "./formatter.js";

const aliases = {
  help: "帮助",
  status: "状态",
  clear: "清空",
};

export function handleCommand(text, userId, store) {
  const command = parseCommand(text);
  if (!command) return null;

  const name = aliases[command.name] || command.name;
  const value = command.value;

  if (name === "帮助") {
    return HELP_TEXT;
  }
  if (name === "状态") {
    return formatProfile(store.get(userId));
  }
  if (name === "清空") {
    store.clear(userId);
    return "已清空当前 QQ 用户的对象设置。长期关系记忆需要在 Web 端单独清理。";
  }
  const setters = {
    对象: "otherAlias",
    阶段: "relationshipStage",
    目标: "goal",
    风格: "userStyle",
  };

  const field = setters[name];
  if (field) {
    if (!value) {
      return "请输入内容，例如：/" + name + " 小林";
    }
    const profile = store.set(userId, { [field]: value });
    return "已更新。\n" + formatProfile(profile);
  }

  return "不认识这个命令。发送 /帮助 查看可用命令。";
}
