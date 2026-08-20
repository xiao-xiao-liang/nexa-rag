import assert from "node:assert/strict";
import test from "node:test";
import { shouldShowToolExecutionBox } from "./tool-execution-state.ts";

test("仅在存在工具调用时展示任务执行卡", () => {
  assert.equal(shouldShowToolExecutionBox([], true), false);
  assert.equal(
    shouldShowToolExecutionBox(
      [
        {
          opId: "1",
          name: "tool1",
          status: "SUCCESS",
          processId: "proc1",
          sequence: 1,
        },
      ],
      false
    ),
    true
  );
});
