import test from "node:test";
import assert from "node:assert/strict";

import {
  parseCommand,
  parseConversation,
} from "../src/lib/parser.js";

test("parse QQ command", () => {
  assert.deepEqual(parseCommand("/对象 小林"), {
    name: "对象",
    value: "小林",
  });
});

test("parse labeled conversation", () => {
  const result = parseConversation(
    "我：今天还顺利吗\n对方：累死了，开了一天会",
    "小林",
  );

  assert.equal(result.length, 2);
  assert.equal(result[0].sender, "我");
  assert.equal(result[1].sender, "小林");
  assert.match(result[1].text, /开了一天会/);
});
