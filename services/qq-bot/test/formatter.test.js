import test from "node:test";
import assert from "node:assert/strict";

import { formatCoachResult } from "../src/lib/formatter.js";

test("format structured coach result", () => {
  const text = formatCoachResult({
    needsClarification: false,
    mainStrategy: "承接",
    diagnosis: "对方在表达疲惫，不适合强行推进。",
    bestReply: "先歇会儿，想吐槽再找我。",
    alternatives: [
      { label: "A｜激进", text: "忙完了我请你喝点东西，出来放个风？", tradeoff: "更主动" },
      { label: "C｜保守", text: "辛苦了，先好好休息。", tradeoff: "更克制" },
    ],
    branches: {
      positive: "顺着聊一两句。",
      ambiguous: "收一点，不追问。",
      reject: "停止推进。",
    },
    nextStep: "先发首选，不补第二条。",
  });

  assert.match(text, /A｜激进/);
  assert.match(text, /B｜正常/);
  assert.match(text, /C｜保守/);
  assert.match(text, /不会自动发送/);
  assert.match(text, /拒绝\/不适/);
});
