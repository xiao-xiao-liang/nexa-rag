import assert from "node:assert/strict";
import test from "node:test";
import { applyCitationSnapshot, linkifyCitationMarkers, normalizeChatStreamEvent } from "./chat-stream-event.ts";

test("将后端 JSON 中的字符串事件版本规范为数值", () => {
  const event = normalizeChatStreamEvent({
    type: "ANSWER_DELTA",
    content: "第二段正文",
    eventVersion: "10",
  });

  assert.equal(event.eventVersion, 10);
});

test("CITATIONS 事件应将可点击编号写入目标助手消息", () => {
  const messages = applyCitationSnapshot([
    { messageId: "m1", sequence: 1, role: "ASSISTANT", status: "COMPLETED", content: "回答 [2]" },
  ], "m1", [{ citationId: 1 }, { citationId: 2 }]);

  assert.deepEqual(messages[0].citations, [{ citationId: 1 }, { citationId: 2 }]);
});

test("相邻引用编号应分别转换为安全链接", () => {
  const content = linkifyCitationMarkers("结论 [2][3]", new Set([2, 3]));

  assert.equal(content, "结论 [2](https://citation.local/2)[3](https://citation.local/3)");
});

test("不在白名单中的引用编号保持原样", () => {
  const content = linkifyCitationMarkers("结论 [1][5][99]", new Set([1]));

  assert.equal(content, "结论 [1](https://citation.local/1)[5][99]");
});

test("复杂多段落中的合法引用编号正确转换", () => {
  const text = "起因 [2]:\n1. 样本差异 [2][3]\n2. 指标不一致 [2][3]\n3. 随机性 [1][2][3]\n4. 优化 [1][4]";
  const result = linkifyCitationMarkers(text, new Set([1, 2, 3, 4]));

  assert.equal(
    result,
    "起因 [2](https://citation.local/2):\n1. 样本差异 [2](https://citation.local/2)[3](https://citation.local/3)\n2. 指标不一致 [2](https://citation.local/2)[3](https://citation.local/3)\n3. 随机性 [1](https://citation.local/1)[2](https://citation.local/2)[3](https://citation.local/3)\n4. 优化 [1](https://citation.local/1)[4](https://citation.local/4)"
  );
});
