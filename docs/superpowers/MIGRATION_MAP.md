# ddd4j 文档迁移映射表

> **状态**：待用户确认
> **生成日期**：2026-08-12
> **当前分支**：feature/3.0.x
>
> **重要**：在用户确认本映射表之前，不会执行任何 `git rm` 操作。

---

## 一、迁移总览

| 类别 | 旧文件数 | 新文件数 | 说明 |
|------|---------|---------|------|
| 计划类（plans） | 5 | 5 | 迁移/重构/发布计划 |
| 设计规格类（specs） | 14 | 14 | 架构设计/流程/规范 |
| 保留不动 | 6 | 6 | 知识库/入口/HTML |
| **合计** | **25** | **25** | |

---

## 二、计划类迁移映射（docs/superpowers/plans/）

| 旧文件 | 新文件 | 日期依据 |
|--------|--------|---------|
| `REFACTOR_MIGRATION.md` | `docs/superpowers/plans/2026-06-27-ddd4j-2x-refactor-migration.md` | 文档内声明"初始重构时间：2026-06-27" |
| `docs/migration/ddd4j-data-optimization-plan.md` | `docs/superpowers/plans/2026-06-29-ddd4j-data-optimization.md` | 文档内声明"最后更新：2026-07-01"，首次审计 2026-06-29 |
| `docs/migration/3.0.0-context-lookup.md` | `docs/superpowers/plans/2026-07-02-3x-context-lookup-migration.md` | git log 首次出现 2026-07-02 |
| `docs/migration/3.0.0-domain-reorganization.md` | `docs/superpowers/plans/2026-07-06-3x-domain-reorganization.md` | git log 首次出现 2026-07-06 |
| `docs/production-release-quality.md` | `docs/superpowers/plans/2026-08-03-production-release-quality.md` | git log 2026-08-03 |

---

## 三、设计规格类迁移映射（docs/superpowers/specs/）

| 旧文件 | 新文件 | 日期依据 |
|--------|--------|---------|
| `docs/architecture/architecture.md` | `docs/superpowers/specs/2026-06-29-ddd4j-architecture-overview-design.md` | Codegraph 同步日期 2026-07-01，文档创建早于该日期 |
| `docs/architecture/architecture-boundary.md` | `docs/superpowers/specs/2026-06-29-ddd4j-boundary-rules-design.md` | git log 首次出现 2026-06-29 |
| `docs/webflux-flow.md` | `docs/superpowers/specs/2026-06-29-webflux-flow-design.md` | git log 首次出现 2026-06-29 |
| `docs/webmvc-flow.md` | `docs/superpowers/specs/2026-06-30-webmvc-flow-design.md` | git log 首次出现 2026-06-30 |
| `docs/architecture/annotation-architecture.md` | `docs/superpowers/specs/2026-07-02-annotation-architecture-design.md` | git log 首次出现 2026-07-02 |
| `docs/auth-satoken.md` | `docs/superpowers/specs/2026-07-02-satoken-auth-design.md` | git log 首次出现 2026-07-02 |
| `docs/architecture/ddd4j-full-architecture-review.md` | `docs/superpowers/specs/2026-07-01-ddd4j-full-architecture-review-design.md` | Codegraph 同步日期 2026-07-01 |
| `docs/architecture/ddd4j-quarkus-adaptation-analysis.md` | `docs/superpowers/specs/2026-07-01-ddd4j-quarkus-adaptation-design.md` | Codegraph 同步日期 2026-07-01 |
| `docs/architecture/ddd-4-java-architecture.md` | `docs/superpowers/specs/2026-06-29-ddd-4-java-architecture-design.md` | git log 首次出现 2026-06-29 |
| `docs/architecture/cqrs-4-java-architecture.md` | `docs/superpowers/specs/2026-07-02-cqrs-4-java-architecture-design.md` | git log 首次出现 2026-07-02 |
| `docs/architecture/ddd-cqrs-4-java-example-architecture.md` | `docs/superpowers/specs/2026-07-02-ddd-cqrs-example-architecture-design.md` | git log 首次出现 2026-07-02 |
| `docs/architecture/current-source-architecture.md` | `docs/superpowers/specs/2026-07-15-current-source-architecture-design.md` | 文档内声明提交日期 2026-07-15 |
| `docs/performance-report-contract.md` | `docs/superpowers/specs/2026-08-03-performance-report-contract-design.md` | git log 2026-08-03 |
| `docs/cloud-mq-rc-evidence.md` | `docs/superpowers/specs/2026-08-03-cloud-mq-rc-evidence-design.md` | git log 2026-08-03 |
| `docs/migration/optional-migrations.md` | `docs/superpowers/specs/2026-07-02-optional-migrations-design.md` | git log 首次出现 2026-07-02 |

---

## 四、保留不动的文件

| 文件 | 保留原因 |
|------|---------|
| `README.md` | 项目入口文件，不属于计划/规范 |
| `docs/ddd/`（全部 6 个文件 + blogs/ + cqrs-overview.png） | DDD 知识库（理论学习资料），不是计划/规范 |
| `docs/architecture/ddd4j_architecture.html` | HTML 架构图（SVG 嵌入），非 Markdown 规范 |
| `CODEGRAPH_LATEST_ZH.md` | 已声明为历史快照，当前内容已整合到 `current-source-architecture.md`，但保留作参考 |
| `docs/DDD4R_MIGRATION.md` | Rust 迁移入口（指向外部 ddd4r 仓库），不属于计划/规范 |

---

## 五、待删除的旧文件清单（需用户确认）

以下文件在用户确认后将执行 `git rm`：

### 根目录
1. `REFACTOR_MIGRATION.md`

### docs/ 目录
2. `docs/production-release-quality.md`
3. `docs/performance-report-contract.md`
4. `docs/auth-satoken.md`
5. `docs/cloud-mq-rc-evidence.md`
6. `docs/webflux-flow.md`
7. `docs/webmvc-flow.md`

### docs/architecture/ 目录
8. `docs/architecture/architecture.md`
9. `docs/architecture/architecture-boundary.md`
10. `docs/architecture/annotation-architecture.md`
11. `docs/architecture/cqrs-4-java-architecture.md`
12. `docs/architecture/ddd-4-java-architecture.md`
13. `docs/architecture/ddd-cqrs-4-java-example-architecture.md`
14. `docs/architecture/ddd4j-full-architecture-review.md`
15. `docs/architecture/ddd4j-quarkus-adaptation-analysis.md`
16. `docs/architecture/current-source-architecture.md`

### docs/migration/ 目录
17. `docs/migration/optional-migrations.md`
18. `docs/migration/ddd4j-data-optimization-plan.md`
19. `docs/migration/3.0.0-context-lookup.md`
20. `docs/migration/3.0.0-domain-reorganization.md`

---

## 六、README.md 中的文档引用需同步更新

删除旧文件后，`README.md` 中的以下引用需要更新为新路径：

| README 中的旧引用 | 新路径 |
|-------------------|--------|
| `./docs/DDD4R_MIGRATION.md` | 保留不动 |
| `./docs/architecture/architecture-boundary.md` | `./docs/superpowers/specs/2026-06-29-ddd4j-boundary-rules-design.md` |
| `./docs/architecture/architecture.md` | `./docs/superpowers/specs/2026-06-29-ddd4j-architecture-overview-design.md` |
| `./docs/architecture/current-source-architecture.md` | `./docs/superpowers/specs/2026-07-15-current-source-architecture-design.md` |
| `./docs/production-release-quality.md` | `./docs/superpowers/plans/2026-08-03-production-release-quality.md` |
| `./docs/migration/ddd4j-data-optimization-plan.md` | `./docs/superpowers/plans/2026-06-29-ddd4j-data-optimization.md` |
| `./docs/migration/optional-migrations.md` | `./docs/superpowers/specs/2026-07-02-optional-migrations-design.md` |

---

## 七、确认清单

请确认以下事项后再执行删除：

- [ ] 映射关系正确（旧文件 → 新文件对应无误）
- [ ] 保留不动的文件列表正确
- [ ] README.md 引用更新方案可接受
- [ ] 确认执行 `git rm` 删除旧文件
