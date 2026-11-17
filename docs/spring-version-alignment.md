# Spring 三线版本一致性 CI

三条维护分支保留相同的 `spring-version-alignment.yml`、检查器及自测。任一维护线 push、面向维护线的 PR、手动运行都会冻结三条线的提交 SHA，再分别使用 JDK 8、17、21 校验。触发分支使用本次事件 SHA；PR 使用待合并的测试提交，不以旧的分支头代替 PR 内容。

| 维护线 | JDK / Maven 编译目标 | 项目版本 | Spring Framework | Servlet API |
|---|---|---|---|---|
| 1.0.x | 8 | 1.0.x.20260630-SNAPSHOT | 公开 5.3.x 终版 5.3.39 | javax.servlet 4.x |
| 2.0.x | 17 | 2.0.x.20260630-SNAPSHOT | 稳定 6.x | jakarta.servlet 6.x |
| 3.0.x | 21 | 3.0.x.20260630-SNAPSHOT | 稳定 7.x | jakarta.servlet 6.1+，仍属 6.x |

Spring 7 的官方最低规范为 Servlet 6.1 / Jakarta EE 11；不能将通过此门禁解释为严格 Jakarta EE 10 容器验收。6.x、7.x 的具体补丁版本由该线依赖父 POM 维护；此门禁校验稳定版本、主线和一致性，不查询网络来认定最新补丁。最新稳定候选和漏洞检查继续由每周依赖维护流程负责。

检查器按最多 25 个项目一批调用仓库自己的 Maven Wrapper，使用 Maven Help 3.5.2 导出真实有效 POM。它不模拟 BOM 优先级，也不执行 install/deploy。每条线单独归档有效 POM、Maven 日志、源码 POM 哈希和 `summary.json`。

阻断条件包括：

- JDK、Java 编译参数、项目 SNAPSHOT 版本或 Servlet 主线不符合维护线要求。
- 根 POM 与依赖父 POM 的共享 Spring 属性冲突，或下游模块覆写共享属性。
- 同一 Spring 坐标在模块有效依赖管理表或直接声明中偏离共享版本，包括尚未被实际引用的管理项。
- Framework、Security、Data、Boot 及已识别 Spring 配套项目混用不同代际。
- 缺失模块、重复有效模型、未解析属性、Maven 导出失败、检查期间 POM 变化。失败不转换为零问题。

普通 BOM 元数据记录与运行时 JAR 分开归档；第三方管理表中的 `spring-framework-bom` 普通条目不被误认为实际平台导入。实际 Framework JAR 必须全部精确匹配共享 Framework 版本。相同值的历史父子属性重复不误报为冲突，但仍建议集中维护。

Spring Data 各产品可以有不同主版本，例如同一 2025.0 BOM 中 JPA、MongoDB、Neo4j 的主版本不同。门禁用 Commons/JPA 核心组件校验平台代际，其余 Data 坐标逐项与中央有效管理表比较，不将所有产品强行套成同一个主版本。

本地示例（先选择对应 JDK）：

```bash
python3 -m unittest scripts/test_spring_version_alignment.py scripts/test_spring_alignment_workflow.py
python3 scripts/verify_spring_version_alignment.py \
  --root . --line 2.0.x --output target/spring-alignment
```

需要本地 Maven 参数时可重复传入 `--maven-arg`，例如 `--maven-arg=-o`；CI 不传离线参数。无需 Docker 或真实业务服务，检查通过不代替单元测试、容器回归、漏洞扫描或生产验收。

官方依据：[Maven 有效 POM](https://maven.apache.org/plugins/maven-help-plugin/effective-pom-mojo.html)、[Maven 依赖管理](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)、[Spring 7 基线](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)。
