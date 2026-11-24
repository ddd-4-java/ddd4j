# 2.0.x 版本归属审查（2026-11-18）

范围：`feature/2.0.x`，基于已有依赖一致性规格增量修复；保持 JDK 17、Spring Framework 6、Security 6、Boot 3。修改中央 POM、版本更新策略与依赖门禁，不修改业务 Java 代码，也不在功能模块新增管理表。

## 已修正配置

| 问题 | 修正 |
|---|---|
| 专用 BOM 与综合平台 BOM 交错，平台可先占用日志、gRPC、JUnit 等基础组件版本 | 全部 BOM 集中到导入区，Spring 和基础组件在前、领域组件其次、综合平台最后；CI 登记全部 75 个导入项，拒绝未登记导入与优先级漂移。 |
| Vert.x 测试分类制品容易被误判为普通 Core 混版 | 13 项均为 `jar:tests`，不是普通运行库覆盖；保留原有 4.5.36 管理值并逐坐标登记，普通运行库仍按 5.2.3 BOM 管理。CI 区分中央测试分类管理值与实际模块的运行时归属。 |
| Kotlin BOM 2.2.21 与 Stdlib 2.0.21 不一致 | `kotlin-stdlib.version=${kotlin.version}`；保持已选 Kotlin 编译器版本。 |
| gRPC BOM 与组件分别维护相同的版本字面量 | `grpc-bom.version=${grpc.version}`；BOM 提前，覆盖 Context 等被综合平台选旧的风险。 |
| CXF 4.2 已进入 Spring 7 / Boot 4 / EE 11 线 | 改为公开的 CXF 4.1.9，并导入 CXF BOM，保留 Spring 6 / EE 10 线。 |
| Tomcat 11 与 Boot 3 默认容器线不符 | 改为 10.1.62，补齐嵌入式与独立 Tomcat 工件的中央版本管理。Tomcat 不存在可供导入的 `tomcat-bom`，没有虚构该坐标。 |
| Initializr 0.24 基于 Boot 4 | 改为 0.23.0；公开 initializr-web POM 对应 Boot 3.5.10、Spring WebMVC 6.2.15，框架最终 Spring 版本仍由中央 Spring 6 BOM 管理。 |
| OpenTelemetry SDK、Alpha 扩展、Instrumentation 和 Semconv 分属不同发行批次 | 使用 Instrumentation 2.33.1 官方配套的 SDK 1.67.0，而非单独保留 SDK 1.68.0；Alpha 扩展跟随各自家族，Semconv 1.44.0，Agent 独立属性 2.33.1。没有新增业务使用的实验能力。 |
| Micronaut 平台版本与更新策略不一致，Core 只管理部分坐标 | 平台 4.10.21、Core 4.10.26；显式导入 Core BOM，并使其优先于综合平台。 |
| jOOQ 可继续升级维护补丁，但新版 OSS 主线要求更高 JDK | 3.19.30 → 3.19.40，保留 JDK 17 的 3.19 线。 |
| Jersey 属性落后于已批准策略 | 3.1.11 → 3.1.12；Helidon 3 仍单独使用 Jersey 3.0.18。 |
| HtmlUnit Driver 直接借用 Selenium 属性，Jetty Maven 插件仍指向旧线 | HtmlUnit Driver 独立属性 4.39.0，其公开 POM 与 Selenium 4.39.0 配套；Jetty 插件改为 EE10 的 `jetty-ee10-maven-plugin`，引用同一 Jetty 属性。 |
| ClassGraph / Jedis 源码与周更固定策略不一致 | 分别由 4.8.184 → 4.8.199、7.5.2 → 7.5.3；两个公开制品已核对，并新增源码与周更策略的漂移门禁。 |
| Maven 组件可能被平台拆分选版 | 引入官方 Maven 父 POM 的管理清单，统一当前 3.9.16 家族。 |

Micrometer 指标 1.17.2 与 Tracing 1.7.2、Netty 默认 4.2.21.Final 与兼容 4.1.141.Final 的既有修复保留。周更策略同步固定上述兼容选择，保护派生 Alpha 属性，避免自动更新再切回不兼容线。

## 没有机械统一的情况

- Dozer Spring 扩展与 Dozer Core 的公开 POM 同步发布，现有别名有效，予以保留。
- Jackson 2 / 3 使用不同坐标；不同运行时的 Hibernate、Netty、Vert.x、Jersey 版本有明确兼容归属。全项目同时列出两条线，不能单独证明同一模块混版。
- 同一 BOM 中也有独立版本工件。门禁读取上游实际导出的版本，不将 BOM 版本直接赋给每个工件。
- 后续经用户授权清理未使用的停维组件：已从中央目录移除 32 个声明及 6 个失效属性，包括旧 CXF 绑定、Resilience4j 1.7.1 集成、Hystrix、旧异步 HTTP、Derby 和旧 API。保留当前维护中的 CXF、Resilience4j、XMLBeans、BC 和 Jakarta API；CXF 的 WSS4J 家族统一管理为公开安全补丁 4.0.3。详见 [退出记录](dependency-retirement-jdk17.md)。源码删除不代表实际依赖或漏洞扫描已通过。

## CI 与证据边界

完整 CI 生成每个 BOM 的独立参考模型、版本归属清单、框架有效模型及实际依赖树，检查未定义/循环属性、重复中央坐标、模块属性遮蔽、版本属性误用和家族混版，随后检查 JDK 17 字节码并执行现有回归。功能模块禁止新增 `dependencyManagement` 的规则保留。

新增离线门禁用例覆盖中央表自身被污染、平台第三方版本不能充当家族基线、缺失参考模型、冲突归属、独立版本组件、模块兼容线与原生 classifier。这些用例本次只编写，未执行。

本次使用当前 POM、公开元数据及公开制品 POM 审查，并只读查看 IDE 已导入库快照。IDE 快照中的旧日志桥接包、gRPC Context、JUnit 聚合包和 OTel 扩展是排查线索，不能代替当前模块依赖树，也不能证明修改后的结果。

遵照用户要求，本地没有执行 Maven、依赖解析、检查器、测试、构建或漏洞扫描。只检查修改文件的语法、结构及差异。尚无本次变更的 CI 运行结果，既有漏洞报告不作为当前版本的安全通过证明。

## 官方依据

- [CXF 4.1 迁移说明](https://cxf.apache.org/docs/41-migration-guide.html)；[CXF 4.2 迁移说明](https://cxf.apache.org/docs/42-migration-guide.html)。
- [Boot 3.5 容器配置](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html)。
- [Initializr Web 0.23.0 公开 POM](https://repo.maven.apache.org/maven2/io/spring/initializr/initializr-web/0.23.0/initializr-web-0.23.0.pom)。
- [OpenTelemetry Instrumentation 2.33.1 发布](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/tag/v2.33.1)。
- [jOOQ JDK 支持矩阵](https://www.jooq.org/download/support-matrix-jdk)。
- [Micronaut Core BOM 4.10.26](https://repo.maven.apache.org/maven2/io/micronaut/micronaut-core-bom/4.10.26/micronaut-core-bom-4.10.26.pom)。
