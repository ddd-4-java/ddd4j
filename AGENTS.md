# 框架分支统一约定

- 所有版本、修复、集成及备份分支禁止声明 Java `record` 类型，包括生产代码、测试、样例及受 Git 管理的 Java 文档片段。
- 值对象使用普通类；不可变模型使用 final 类、final 字段及显式构造器。保留既有组件名访问器、构造验证、值相等性和 JSON 契约，不为跨分支一致性升级 JDK 或依赖。
- 提交前执行 `python3 scripts/check_no_records.py`；检查忽略字符串与注释，只拒绝实际类型声明。检查器自测执行 `python3 -m unittest discover -s scripts -p test_check_no_records.py`。
- 涉及类型迁移时必须执行受影响模块构建与行为回归；源码扫描通过不代替编译、序列化或业务验证。

## 在本仓库定位改动

本线使用 JDK 21 和 Maven Wrapper 4.0.0-rc-6。

从对应模块的 AGENTS.md 进入。共享领域接口、消息协议、容器接线和业务示例分别由不同模块维护。

| 要改的行为 | 首先检查 |
|---|---|
| 聚合、仓储、命令总线和事件存储的公共承诺 | [core](ddd4j-core/AGENTS.md)，持久化实现见 [data](ddd4j-data/AGENTS.md) |
| broker 确认、重投和生产者所有权 | [MQ 协议与后端](ddd4j-mq/AGENTS.md) |
| 请求上下文、身份或生命周期 | [Web 适配](ddd4j-web/AGENTS.md)、[鉴权引擎](ddd4j-auth/AGENTS.md)、[运行时装配](ddd4j-runtime/AGENTS.md) |
| 四表交易或七种框架的 28 个入口 | [示例维护矩阵](ddd4j-samples/AGENTS.md) |
| 对外工件、第三方版本或下游构建插件 | [BOM](ddd4j-bom/AGENTS.md)、[dependencies](ddd4j-dependencies/AGENTS.md)、[parent](ddd4j-parent/AGENTS.md) |

模块中的 Maven 命令均从仓库根目录执行。带 `-am` 会构建所选项目的上游；测试位于消费模块时，直接选择消费模块。定向测试中的 `-Dsurefire.failIfNoSpecifiedTests=false` 允许上游缺少同名测试，验收时仍需在目标模块的 Surefire 报告中看到指定测试实际执行。
