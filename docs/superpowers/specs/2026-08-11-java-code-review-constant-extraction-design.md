# Java 代码审查常量抽取规则设计

## 目标

扩展现有 `java-code-review` skill，使其能够识别生产 Java 代码中应抽取到领域常量类的字面量与 `private static final` 基础类型字段，并以 Warning 报告可操作的整改建议。

## 方案

在 `java-code-review/SKILL.md` 的 Checklist 中增加“常量抽取”入口，并新增 `references/constant-extraction-rule.md` 承载判定规则、反例和审查输出要求。保持现有 Skill 的触发范围、审查模式和其他专项规则不变。

## 术语

| 名称 | 含义 | 所有者 | 非含义 |
| --- | --- | --- | --- |
| 领域常量类 | 按技术或业务职责组织的、命名为 `*Constant` 的常量类 | 所属 Maven 模块 | 跨模块通用常量桶 |
| 常量抽取 | 将符合范围的基础类型或字符串字面量迁移至领域常量类，并静态导入使用 | 生产代码 | 将日志、异常或展示文案机械迁移 |

## 强制规则

1. 仅审查 `src/main/java`；测试代码不适用。
2. 所有 `private static final` 的字符串和基础数值字段必须抽取，不以复用次数作为豁免条件。
3. 具有领域、协议或业务阈值语义的字符串和数值字面量必须抽取；使用方通过静态导入引用。
4. 常量类放在所属 Maven 模块根包的 `constants` 包，采用单数 `*Constant` 后缀，并按功能拆分；禁止为复用引入逆向或跨层模块依赖。
5. 除 `@ConditionalOnProperty` 外的注解参数适用此规则。注解占位符必须保存为完整、可直接用于注解的编译期字符串常量。
6. 常量抽取违规统一报告为 `⚠️ Warning`，不归类为 Must Fix。

## 例外

1. 日志、异常与展示文案不抽取。
2. `@ConditionalOnProperty` 的 `prefix`、`name`、`havingValue` 以及布尔、数值属性不抽取。
3. `Map`、`List`、`Set`、数组与其他对象类型的 `private static final` 字段不抽取。
4. `0`、`1`、`-1` 等用于索引、循环、比较、算术或通用 API 边界的技术字面量不抽取；具有领域、协议或业务阈值含义时仍须抽取。

## 典型场景

- `FeishuCliDocumentExporter` 的 CLI 命令片段、外部协议字段和长度阈值分别进入职责明确的飞书常量类。
- `FeishuBlockMarkdownConverter` 的 Block 类型整数进入 `FeishuBlockTypeConstant`；`NON_TEXT_BLOCK_LABELS` 保持类内。
- RocketMQ 消费者的 topic、consumer group 占位符和重试次数进入 `RocketMQConstant`，其中占位符保存完整表达式；`@ConditionalOnProperty` 参数保持字面量。

## 验收

1. 审查器在上述典型场景中识别违规并给出精确定位、目标常量类及静态导入建议。
2. 审查器不报告任何已声明例外。
3. 输出模板具有独立的 Warning 分组，且不影响 Must Fix、Should Consider 和 Optional Nits 的既有语义。
