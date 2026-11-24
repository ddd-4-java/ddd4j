# 2.0.x 依赖版本治理

版本定义集中在 `ddd4j-dependencies/pom.xml`。功能模块不维护自己的 `dependencyManagement`；遇到平台兼容差异时，排除冲突传递依赖，再显式声明所需依赖并引用中央属性。
合法的中央依赖 POM 与框架发布 BOM 不在此限制范围内。

## 当前兼容选型

| 运行时 | 选型 |
|---|---|
| Spring 6 / Boot 3 | Hibernate 6.6 / JPA 3.1；避免 SchemaManager 返回类型冲突。 |
| Micrometer 指标与追踪 | 指标库 `micrometer.version=1.17.2`，Tracing `micrometer-tracing.version=1.7.2`；分别管理，不能互相复用版本号。Context Propagation 由指标 BOM 管理为独立的 1.2.1。 |
| CXF / Tomcat / Initializr | CXF 4.1.9、Tomcat 10.1.62、Initializr 0.23.0；分别保留 Spring 6、Servlet 6.0 容器、Boot 3.5 的兼容线。 |
| Kotlin / gRPC | Stdlib 引用 Kotlin BOM 的 `kotlin.version=2.2.21`；gRPC BOM 引用 `grpc.version=1.82.1`，防止重复字面量漂移。 |
| OpenTelemetry | SDK 1.67.0 / Instrumentation 和 Agent 2.33.1 / Semconv 1.44.0；按 Instrumentation 官方配套组合管理，已使用的 Alpha 工件跟随各自家族派生版本。 |
| 默认网络栈、Micronaut 当前 HTTP 实现 | Netty 4.2，由 `netty.version=4.2.21.Final` 管理；当前 Micronaut HTTP 使用 NioIoHandler，不能强制放到 4.1。 |
| Quarkus / Helidon 3 | 显式引用中央 `netty-4_1.version=4.1.141.Final`；Quarkus 使用 Vert.x 4。 |
| Quarkus Panache | 显式选择平台对应的 ORM / Agroal / JPA 3.2，不修改 Spring 的默认管理版本。 |
| Helidon 3 | Jersey 3.0、CDI 3 和配套 Jakarta API；Web 模块维持 provided 范围。 |
| Dropwizard 5.0.2 | Logback 1.6.7；通过访问日志兼容工厂避免已删除的 DEFAULT_CONVERTER_MAP，保留 Appender、默认格式和异步处理。此适配尚未回归。 |

新增的显式依赖来自原已解析的依赖树，目的是保持组件选择，不扩展业务能力。跨项目消费仍须在最终应用的依赖树上验收。

## CI

`dependency-family-alignment.yml` 使用 JDK 17 构建，检查全部有效模型、实际依赖树、原生 classifier 与基础字节码，随后执行回归。
Maven 构建前先执行 `--source-only`：检查中央版本归属、BOM 导入顺序及功能模块不得新增管理表。Micrometer 指标 BOM 和 Tracing BOM 必须各导入一次、引用各自属性，并优先于 Hibernate / Boot / Debezium / Dropwizard / Helidon / Micronaut / Quarkus 平台。
检查器拒绝功能模块再次增加管理表，并校验 Spring / Netty / Reactor / ASM / Jackson / OTel / Micrometer / AWS / JDBI / Jersey / JAXB / ORM 等版本关系。
`scripts/dependency-version-owners.policy` 登记全部导入 BOM 的组件归属、属性和优先级。完整 CI 为每个上游 BOM 创建无框架父 POM 的独立参考模型，只读取该 BOM 负责的组件家族，再核对中央有效管理表、模块声明及实际依赖树。不能用已经被平台覆盖的中央表作为正确基线。参考模型缺失、归属冲突、属性未定义或循环、未登记 BOM、模块属性遮蔽均阻断。
独立发布的工件保留参考 BOM 导出的版本，例如 Context Propagation、gRPC Kotlin Stub 和 Vert.x Docgen；不将 groupId 相同视为版本必须等于 Core。Quarkus / Helidon / Panache 的兼容选择按模块登记，仅影响实际声明与依赖，不改变中央默认管理表。Netty 原生 classifier 同样校验。
Micrometer 的 Core、Commons、Observation、Registry 等直接对照指标版本属性；Tracing 及桥接组件直接对照追踪版本属性。中央有效管理表本身也接受同样校验，不能用已混版的中央结果认可子模块。JAXB Runtime / Core / TXW2 / XJC / JXC 同样直接对照中央 JAXB 属性。
截图涉及的 Logback、BC LTS、JSON Smart / Accessors Smart、Zstd、Shiro、JAXB 运行时和 org.json 同时校验中央版本与实际解析结果，并拒绝降低安全基线。实际依赖（包括 test）出现 Android JSON 或默认 javax Ehcache 变体时失败。该规则不能替代漏洞数据库扫描。
现有三线 Spring 检查保留。2.0.x 的周更扫描改为读取完整有效管理目录和实际依赖清单；管理表中的版本区间单独记录，不伪装成已解析制品。

```mermaid
flowchart LR
  A[中央版本定义] --> B[排除冲突传递依赖]
  B --> C[显式依赖引用中央属性]
  C --> D[有效模型与实际依赖树]
  D --> E[CI 一致性与兼容边界]
  E --> F[回归和漏洞报告]
```

## 使用

```sh
python3 scripts/verify_dependency_family_alignment.py --root . --output target/dependency-families --check-bytecode
```

此命令需要 JDK 17 及已解析的 Maven 制品，自定义私服 settings 通过 `--maven-arg` 传递。
**本次最后配置修复后未完成最终回归；没有远端 CI 通过结论。** 已知遗留及扫描边界见 [漏洞清单](security/2.0.x-public-dependency-residuals.md)。

2026-11-18 的 Micrometer 修复仅交付源码、依赖策略、CI 及回归用例；遵照用户要求没有在本地运行 Maven、测试或漏洞扫描。官方依据：[指标库 1.17.2 发布](https://github.com/micrometer-metrics/micrometer/releases/tag/v1.17.2)、[Tracing 1.7.2 发布及配套指标库版本](https://github.com/micrometer-metrics/tracing/releases/tag/v1.7.2)、[Java 基线](https://github.com/micrometer-metrics/micrometer/blob/v1.17.2/README.md)。公开制品 POM 未要求引入 Spring 7；Spring 6 / Boot 3 的实际集成仍需 CI 回归，不能把源码兼容判断当成已通过验收。

同日扩展审查及变更详见 [版本归属审查](dependency-version-ownership-audit.md)。该扩展也没有在本地执行检查器、测试、构建、依赖解析或漏洞扫描；新增门禁用例留给 CI 执行。

后续旧组件清理删除 32 个未使用的管理声明和 6 个版本属性，补齐 CXF 4.1.9 对应的 WSS4J 4.0.3 家族管理。源码直接声明和实际依赖均拒绝退出坐标；上游 BOM 尚存的未使用管理项单独报告，保留完整漏洞扫描输入。详情见 [旧依赖退出记录](dependency-retirement-jdk17.md)。本次仍未执行本地测试或 Maven。

## Logback 1.6 的 Dropwizard 接线

上游 `LogbackAccessRequestLogFactory.build` 将布局工厂写死，布局又在静态初始化中引用已删除的 `classic.PatternLayout.DEFAULT_CONVERTER_MAP`，无法通过直接继承原布局规避。兼容工厂沿用 Dropwizard 的 Appender、异步工厂及安全请求参数转换器，仅替换布局并通过实例转换器注册。

`Ddd4jBundle` 和 `Ddd4jDropwizardWebBundle` 在服务创建前自动适配上游默认访问日志工厂。应用自定义工厂及空 Appender 的禁用配置保持原样。单独使用 CQRS / Projection、未安装这两个 Bundle 的应用，应在 `Application.run` 中调用 `Ddd4jLogbackAccessRequestLogFactory.configure(configuration)`；仓库中的独立 CQRS 样例及两项真实启动测试已同步接线。第三方自定义工厂若自行调用旧布局，仍需消费方迁移，不能由本修复保证兼容。
