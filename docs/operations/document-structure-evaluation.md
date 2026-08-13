# 文档结构与检索回归评测

LLM 标题精修默认关闭。启用前必须使用脱敏的真实问答样本，对比改造前后结果，并同时满足 Recall@K、结构可用率和标题路径完整率的目标阈值。

## 样本标注

每条样本至少包含以下人工标注：

| 字段 | 说明 |
| --- | --- |
| `caseId` | 稳定且唯一的样本标识 |
| `question` | 用户真实提问；不保存凭据、个人信息或不必要的正文 |
| `expectedRelevantChunkIds` | 人工确认可支撑答案的一个或多个正文 `chunkId` |
| `expectedHeadingPath` | 人工确认的完整标题路径 |
| `actualHeadingPath` | 当前切分结果中该正文片段所属的标题路径 |

不要把未脱敏的整篇云文档、原始图片或模型提示词写入回归集。

## 执行方式

将问答标注构造成 `RetrievalEvaluationCaseBO`，以实际检索函数传给
`RetrievalEvaluationCalculator#evaluateRetrieval`；将标题路径标注构造成
`StructureEvaluationCaseBO`，再调用 `evaluateStructure`。前者输出 Recall@K 和 HitRate@K，后者输出结构可用率和标题路径完全匹配率。

建议固定同一批样本、同一模型路由和相同的 `topK`，分别执行 LLM 兜底关闭与开启两次，记录两份报告。只有提升不以结构完整率下降为代价时，才将以下配置改为 `true`：

```yaml
nexa:
  parser:
    artifact:
      structure:
        llm-fallback:
          enabled: false
```

启用时还必须配置可用的 `route-key`；缺失该配置会在应用启动时失败，避免无意中回退到不确定的模型路由。
