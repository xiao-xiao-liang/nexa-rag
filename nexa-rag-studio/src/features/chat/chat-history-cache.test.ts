import assert from "node:assert/strict";
import test from "node:test";
import {
  emptyHistoryCache,
  getHistoryEntry,
  prependHistoryPage,
  putHistoryPage,
} from "./chat-history-cache.ts";
import { ChatMessageVO } from "../../types";

const message = (messageId: string, sequence: number): ChatMessageVO => ({
  messageId,
  sequence,
  role: "ASSISTANT",
  status: "COMPLETED",
  content: messageId,
});

test("向前分页会去重并保持消息序号升序", () => {
  const firstPage = putHistoryPage(emptyHistoryCache(), "c1", [message("m3", 3), message("m4", 4)], true, 3);
  const merged = prependHistoryPage(firstPage, "c1", [message("m1", 1), message("m2", 2), message("m3", 3)], false);

  assert.deepEqual(getHistoryEntry(merged, "c1")?.messages.map((item) => item.sequence), [1, 2, 3, 4]);
});

test("超过二十个会话时淘汰最久未访问缓存", () => {
  let cache = emptyHistoryCache();
  for (let index = 1; index <= 21; index += 1) {
    cache = putHistoryPage(cache, `c${index}`, [message(`m${index}`, index)], false);
  }

  assert.equal(getHistoryEntry(cache, "c1"), undefined);
  assert.deepEqual(getHistoryEntry(cache, "c21")?.messages.map((item) => item.messageId), ["m21"]);
});
