# JDK 17 旧依赖退出记录（2026-11-18）

范围为 `feature/2.0.x`，沿用现有依赖一致性规格。用户授权删除未使用的停维声明、为仍需保留的组件选用兼容 JDK 17 / Spring 6 的公开版本；不修改业务逻辑，不执行测试或构建。

## 已移除

受 Git 管理的全部 POM（含 profile、插件依赖）未发现以下旧坐标的模块依赖声明，受管源码及配置也未发现相应旧 API / 驱动引用。中央目录共删除 32 个管理声明。

| 类别 | 数量 | 删除内容 |
|---|---:|---|
| 旧 CXF | 7 | HTTP / JBI / Object 绑定，JiBX / SDO / XMLBeans 数据绑定，以及 OAuth 1 集成。保留 CXF 4.1 当前支持的绑定和 OAuth2。 |
| 旧 Resilience4j | 3 | Prometheus、Ratpack、Retrofit 的 1.7.1 集成；不将其伪装成不存在的 2.4.0 坐标。 |
| Hystrix | 3 | Core、Metrics Event Stream、Feign Hystrix；避免 Feign 适配器重新引入停维组件。 |
| Commons HttpClient | 1 | 已 EOL 的 HttpClient 3.1。 |
| 旧异步 HTTP / NIO | 6 | HttpAsyncClient、Cache、OSGi、父 POM；HttpCore NIO 及捆绑了 NIO 的 HttpCore OSGi。 |
| Derby | 7 | Engine、Client、中文资源、Network Server、Optional Tools、Shared、Tools。Derby 已于 2025-10-10 退休，不存在继续等待官方维护版的路径。 |
| 旧 BC Provider | 1 | `bcprov-jdk15on:1.70`。 |
| 旧 API | 4 | `javax.servlet-api`、`javax.validation:validation-api`、独立 StAX API、`xml-apis`。本线保留 Jakarta Servlet / Validation，标准 XML API 使用 JDK 17 模块。 |

同时删除 6 个已无引用的属性：`commons-httpclient.version`、`hystrix.version`、`derby.version`、`javax-validation-api.version`、`xml-apis.version`、`httpasyncclient.version`。完整坐标、原因及替代方向见 [机器可读清单](retired-dependencies-jdk17.policy)。

没有向应用自动添加替代依赖、迁移数据库、调整熔断策略或重写 HTTP 调用。上述退出坐标在本仓库没有直接使用点，不需要改变业务调用。外部应用如果曾依赖这些公共管理声明，需要明确迁移，不能将坐标更换视为二进制兼容。

## 保留和配套版本

- CXF 保持 4.1.9。补齐 6 个 WSS4J 工件的统一管理，版本为 4.0.3：该公开安全补丁属于 JDK 17 的 4.0.x 线，同时满足 CXF 4.1.9 对 SignatureConfirmation 新实现的最低 4.0.2 要求。没有新增运行时依赖，仅管理现有 CXF 能力的配套版本。
- Resilience4j 当前集成保持 2.4.0，包括仍公开发布的 `framework-common`；Prometheus 对接方向为当前 Micrometer 集成。
- 正在使用的 BC `jdk18on` 1.87 与特定运行时所需的 LTS 2.73.13 保留；`jdk18on` 指 Java 1.8 起，不是 JDK 18 起，相关误导性注释已修正。
- 独立 XMLBeans 5.3.0 保留。CXF 的旧 XMLBeans 数据绑定退出，不意味着现代 XMLBeans 库退出。
- HttpClient Classic 4.5.14 / HttpCore Classic 4.4.16 仍有重大缺陷和安全修复支持，保留其兼容用途；5.x 保持已有的 HttpClient 5.6.5 / HttpCore 5.4.3。不能将所有 4.x 坐标都视为已停维，也不能用 5.x Jar 原地替换 4.x API。
- `javax.cache` 和 `javax.money` 不属于本次退出对象，不能仅根据 `javax` 前缀认定过时。

## CI 与证据边界

源码门禁检查所有直接声明，包括中央管理、profile 和插件依赖；旧坐标重新声明即失败，排除项不会被误判为声明。实际依赖树中的直接或传递旧制品同样失败，保留模块、类型、classifier 和引入路径。WSS4J 家族另行校验统一版本。

综合平台 BOM 可能仍导出旧坐标的管理元数据。Maven 不能通过普通 exclusion 删除导入 BOM 的管理条目；这类尚未引入的元数据单列到 `retired-managed-entries.json`，不当作运行时使用，也不从管理目录漏洞扫描中隐藏。声明或解析到实际依赖仍会被阻断。

三线组件矩阵仅将这 32 项的 JDK 17 单元标记为 `removed-unused`，不是字节码或回归通过；JDK 8 / 21 数据保留。对应覆盖策略登记 `unsupported-retired`，三线差异策略只更新这两个配置文件的 JDK 17 内容指纹，不放宽实际依赖、漏洞或字节码检查。

本次只执行源码、公开 POM / 生命周期资料审查，以及修改文件的语法和差异检查。新增门禁回归用例留给 CI，没有本地测试、构建、依赖解析或重新漏洞扫描结果。历史扫描 CSV 保留为历史证据，不能据此宣布当前只剩 Spring 漏洞。

## Pulsar、TDMQ 与 EventStoreDB 的旧验证 API

Java 17 的 DDD4J 适配器在 Pulsar 客户端和 EventStoreDB gRPC 客户端的依赖边排除 `javax.validation:validation-api`。DDD4J 发布订阅仍使用 Pulsar 字节消息协议，TDMQ 复用该协议适配。Java 17 的原生测试覆盖 Pulsar 发布消费、TDMQ 协议以及 EventStoreDB 追加、读取、版本冲突和事件元数据。

直接使用底层 SDK 的应用可以继续按需声明旧 API。若调用 Pulsar 内置的 Bean Validation JSON Schema 转换器，显式声明 `javax.validation:validation-api:1.1.0.Final`。若通过反射读取 EventStoreDB 原始 SDK 对象上的旧 `@NotNull`，显式声明 `javax.validation:validation-api:2.0.1.Final`。已在相同 SDK 类路径验证这两项显式接入。应用同时使用这两项 SDK 功能时，单独声明 `javax.validation:validation-api:2.0.1.Final` 也通过了两项验证。DDD4J 的应用验证仍使用 Jakarta Validation。

## 官方依据

- [CXF 已移除的数据绑定](https://cxf.apache.org/docs/databindings.html)，[CXF 4.1 及 WSS4J 配套要求](https://cxf.apache.org/docs/41-migration-guide.html)。
- [Resilience4j 2.4.0 公开 BOM](https://repo.maven.apache.org/maven2/io/github/resilience4j/resilience4j-bom/2.4.0/resilience4j-bom-2.4.0.pom)，[当前 Micrometer 集成](https://resilience4j.readme.io/docs/micrometer)。
- [Hystrix 维护状态](https://github.com/Netflix/Hystrix#hystrix-status)。
- [Apache HttpComponents 各分支状态](https://hc.apache.org/status.html)，[HttpCore OSGi 的 NIO 依赖](https://repo.maven.apache.org/maven2/org/apache/httpcomponents/httpcore-osgi/4.4.16/httpcore-osgi-4.4.16.pom)。
- [Derby 退休公告](https://db.apache.org/derby/)。
- [WSS4J 4.0.x Java 基线和 4.0.3 安全发布](https://ws.apache.org/wss4j/)，[公开 4.0.3 制品](https://repo.maven.apache.org/maven2/org/apache/wss4j/wss4j-ws-security-dom/4.0.3/wss4j-ws-security-dom-4.0.3.pom)。
