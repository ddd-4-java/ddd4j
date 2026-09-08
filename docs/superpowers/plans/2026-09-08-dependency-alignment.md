# 三线依赖清单对齐

事实源：`../specs/2026-09-06-three-line-parity.md` 的 D1；用户已批准实施。

- [x] 保存直接管理清单与 Maven effective POM 基线，建立能检出真实遗漏及 JDK 违规的门禁并运行 RED。
- [ ] 核验差异组件的 BOM 覆盖、替代坐标和发行版 JDK 要求，形成逐项清单。
- [ ] 修改三线 POM，统一普通组件，按 JDK 和框架保留必要差异。
- [ ] 运行清单、模型、产物/JDK 门禁及受影响模块回归，记录未验证项与剩余风险。

初始状态：JDK8 checkout `6d217054` detached HEAD，已有 `docs/migrations/truelicense4.md` 修改；JDK17 `90ab6cc3` feature/2.0.x；JDK21 `2e730fb4` feature/3.0.x。保留所有状态，不切换分支。

## 当前进度

已完成本轮直接声明与 effective POM 比较、版本修正、坐标/聚合 POM 类型修正，以及三线 sample/依赖回归。严格门禁仍未通过，不能勾选整体对齐完成：当前有 26 个受限/迁移条目和 195 个未验证产物行（78 个唯一坐标/版本组合）。

完整结果见 [本轮结果](2026-09-08-dependency-alignment-results.md)，实际矩阵与逐项状态在 `config/dependencies/`。公开坐标错误中仍有需要按 API/框架世代迁移的 Swagger、Dubbo、Jetty、Resilience4j 等；不能通过填写猜测版本关闭条目。
