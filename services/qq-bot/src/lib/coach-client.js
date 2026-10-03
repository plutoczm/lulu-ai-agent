export async function requestCoach(config, payload, signal) {
  const response = await fetch(config.coachApi, {
    method: "POST",
    headers: {
      "content-type": "application/json; charset=utf-8",
      "X-Lulu-Channel-Token": config.channelToken || "",
    },
    body: JSON.stringify(payload),
    signal,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(
      "Coach API failed: HTTP " + response.status + " " + text.slice(0, 500),
    );
  }

  return response.json();
}

export function buildCoachPayload({
  platform = "qq",
  senderId,
  profile,
  messages,
}) {
  const alias = profile.otherAlias || "对方";
  return {
    platform,
    conversationId: ["qq", senderId, alias].join(":"),
    userAlias: "我",
    otherAlias: alias,
    relationshipStage: profile.relationshipStage || "未提供",
    goal: profile.goal || "自动判断",
    userStyle: profile.userStyle || "短句、自然、不油腻",
    messages,
  };
}
