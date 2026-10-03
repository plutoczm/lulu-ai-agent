import { readConfig } from "./lib/env.js";
import { parseConversation } from "./lib/parser.js";
import { buildCoachPayload, requestCoach } from "./lib/coach-client.js";
import { formatCoachResult } from "./lib/formatter.js";

const config = readConfig();
const profile = {
  otherAlias: "小林",
  relationshipStage: "暧昧期",
  goal: "自然承接，不显得过度用力",
  userStyle: "短句、自然、偶尔开玩笑，不油腻",
};

const messages = parseConversation(
  "我：今天还顺利吗\n对方：累死了，今天开了一天会",
  profile.otherAlias,
);

const payload = buildCoachPayload({
  platform: "qq",
  senderId: "smoke-user",
  profile,
  messages,
});

const result = await requestCoach(config, payload);
console.log(formatCoachResult(result));
