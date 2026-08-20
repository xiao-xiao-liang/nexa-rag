import assert from "node:assert/strict";
import test from "node:test";
import { normalizeChatStreamEvent } from "./chat-stream-event.ts";

test("将后端 JSON 中的字符串事件版本规范为数值", () => {
  const event = normalizeChatStreamEvent({
    type: "ANSWER_DELTA",
    content: "第二段正文",
    eventVersion: "10",
  });

  assert.equal(event.eventVersion, 10);
});
