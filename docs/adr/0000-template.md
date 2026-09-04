# ADR-NNNN: <决策标题>

> **使用说明**：新建 ADR 时复制本模板为 `docs/adr/NNNN-kebab-case.md`（编号从 0001 起递增、四位零填充，不复用已废弃编号），保留下列 5 节且顺序不变。编号与文件名一旦提交即不可更改；状态一旦 Accepted，只能通过新的 ADR 推翻——在新 ADR 中说明替代关系，并在旧 ADR 的 Status 标注「Superseded by ADR-XXXX」，不回改决策正文。写作约定：Decision 只写「决定了什么」；背景、约束与证据（含相对路径引用，如 `../reference/fuin-api-patterns/01-aggregate-root.md`）放 Context；代价与后续义务放 Consequences；被否决的候选方案及否决理由放 Alternatives Considered。正文使用全角标点，每篇控制在 60-120 行。

## Status

（Accepted / Proposed / Superseded by ADR-XXXX）

> 记录日期：YYYY-MM-DD；状态变更时在此追加变更日期与替代 ADR 编号。

## Context

（背景：面临什么问题、受什么约束、有哪些证据。引用既有文档时给出相对路径与关键结论，不整段复制。）

## Decision

（决定：以加粗标出核心决定，逐条列出可执行的决策内容，使读者无需推断即可执行。）

## Consequences

（后果：正面与负面影响分列；含决策带来的后续义务（如补测试、迁移、文档），义务须注明落地任务编号。）

## Alternatives Considered

（备选方案：每个方案一小节或一个列表项，写明方案内容与被否决的理由；已被本 ADR 取代的方案不得删除。）
