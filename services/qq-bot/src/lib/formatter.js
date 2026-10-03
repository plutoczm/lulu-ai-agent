function clean(text) {
  return String(text || "").trim();
}

function cut(text, max = 2200) {
  const value = clean(text);
  return value.length > max ? value.slice(0, max - 1) + "…" : value;
}

export function formatProfile(profile) {
  return [
    "噜噜当前设置",
    "对象：" + (profile.otherAlias || "对方"),
    "阶段：" + (profile.relationshipStage || "未提供"),
    "目标：" + (profile.goal || "自动判断"),
    "风格：" + (profile.userStyle || "短句、自然、不油腻"),
  ].join("\n");
}

export function formatCoachResult(result) {
  if (!result) return "这次没有生成有效建议，请再试一次。";

  if (result.needsClarification) {
    return [
      "还差一个关键信息：",
      clean(result.clarificationQuestion),
    ].join("\n");
  }
  const alternatives = result.alternatives || [];
  const aggressive = alternatives[0] || {};
  const conservative = alternatives[1] || {};
  const lines = [
    "噜噜给你 3 条参考回复（不会自动发送）：",
    "",
    "A｜激进",
    clean(aggressive.text),
    "",
    "B｜正常",
    clean(result.bestReply),
    "",
    "C｜保守",
    clean(conservative.text),
  ];

  if (result.diagnosis) {
    lines.push("", "参考判断：" + clean(result.diagnosis));
  }

  const branches = result.branches || {};
  lines.push("", "对方接下来如果——");
  if (branches.positive) {
    lines.push("积极：" + clean(branches.positive));
  }
  if (branches.ambiguous) {
    lines.push("含糊：" + clean(branches.ambiguous));
  }
  if (branches.reject) {
    lines.push("拒绝/不适：" + clean(branches.reject));
  }

  if (result.nextStep) {
    lines.push("", "现在只做这一件事：", clean(result.nextStep));
  }

  return cut(lines.filter(Boolean).join("\n"));
}

export function formatErrorMessage(error) {
  const message = clean(error?.message);
  if (message.includes("Coach API failed")) {
    return "噜噜后端暂时没有响应，请稍后再试。";
  }
  return "这次处理失败了，请稍后再试。";
}
