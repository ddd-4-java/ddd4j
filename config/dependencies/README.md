# 三线依赖清单与验证

本目录记录 JDK 8 / 17 / 21 三线的依赖治理结果。规格事实源仍为
`ddd4j/docs/superpowers/specs/2026-09-06-three-line-parity.md` 的 D1。本次不是全量兼容性验收通过状态。

- `alignment-policy.json`：原始组件基线、已核验坐标替代，以及明确未完成的兼容/迁移项。固定基线避免通过删除声明或移入 profile
  缩小检查范围。
- 第三方库版本不从 DDD4J 分支号推导；只有 `dependency_versions` 中显式配置的坐标才校验指定版本（字符串表示三线共享版本，JDK 键表示明确的平台版本）。EasyExcel 五个发布模块统一固定为正式版 `5.0.1`。构件解析、JAR 字节码、组件覆盖和漏洞扫描门禁继续执行。
- `version-evidence.json`：部分兼容发行版的发布 JAR 字节码证据。
- 每周升级策略针对 3.0.x 的 Dropwizard 固定 `hibernate-validator.version=8.0.6.Final`、`jersey.version=3.1.12`、`logback.version=1.5.38`：当前 Dropwizard 5.0.2 的真实启动测试依赖 Validator 8、Jersey 3 和 Logback 1.5 的内部 API；升级到 9、4、1.6 分别导致缺类或缺字段。其余未固定依赖仍寻找最新稳定版；固定项照常进入漏洞扫描，候选仍须通过构建与测试。
- 1.0.x 的 JUnit Jupiter 固定为 `5.14.4`、Platform Launcher 固定为 `1.14.4`，Mockito 固定为最新的 4.x 版本 `4.11.0`，SLF4J legacy 通道固定为 `1.7.36`，Logback 主线及 legacy 通道固定为 `1.2.13`，SLF4J 2 测试通道固定为 `1.3.14`，Javalin 固定为最新的 JDK 8 兼容 4.x 版本 `4.6.8`，Dropwizard 固定为最新的 2.1.x 版本 `2.1.12`，Hibernate Validator 固定为 JDK 8 兼容的最新 6.2.x 版本 `6.2.6.Final`，Caffeine 固定为 JDK 8 兼容的最新 2.9.x 版本 `2.9.3`，Vert.x stack 固定为 4.5.x 最新版 `4.5.35`，Quarkus 固定为 JDK 8/`javax.enterprise` 对应的 1.13.x 最新版 `1.13.7.Final`。自动升级到 JUnit 6.1.3 会引入 class 61，超出 JDK 8 的 class 52；JUnit 和 Mockito 固定构件的关键类经 Maven Central JAR 验证仍为 class 52；Mockito 5.x 的 class 55 超出 JDK 8；Logback 1.6 不提供 SLF4J 1.7 的 MDC 绑定，导致 Core 的追踪上下文测试失败；Javalin 5.x/7.x 的主要类分别为 class 55/61，超出 JDK 8；Dropwizard 5.x 也不再提供本线使用的 `io.dropwizard.Application` API；Hibernate Validator 9.x 的 class 61 与 Caffeine 3.x 与 Vert.x 5.x 的 class 55 均无法由 JDK 8 运行。固定版本照常进入漏洞扫描。
- 1.0.x 的 `microprofile-config-api` 排除自动升级：Quarkus 1.13.7.Final 的配置接口使用 MicroProfile Config 1.4；自动升级到 3.1.2 会在真实容器测试触发 `AbstractMethodError`。排除项仍进入漏洞扫描。
- 3.0.x 的 Micronaut 固定在平台 `4.10.21`、Core `4.10.26`，并排除 `io.micronaut*:*` 的自动升级。Micronaut 5.2.x 的构件含 class 69（JDK 25），无法由本线 JDK 21 编译；Micronaut 4 系列的 CQRS 模块容器集成测试已通过。固定与排除构件仍进入每周漏洞扫描。
- 1.0.x 的 Helidon MicroProfile Server、Health 及样例 BOM 保持 `1.4.19`：候选 4.5.5 的 Server 为 class 65，JDK 8 无法编译；隔离候选恢复 1.4.19 后，真实 Server 启停在内的运行时测试 23/23 通过。固定构件仍扫描漏洞。
- 1.0.x 的 Micronaut 运行时显式使用 Jakarta Annotation API `2.1.1`：Micronaut 3.10.15 的候选依赖图带入 3.0.0（class 55），导致 JDK 8 编译失败；直接声明 2.1.1 后该模块 10 项测试通过。此坐标仅在 1.0.x 排除自动升级，仍扫描漏洞。
- 1.0.x 的 Pulsar 客户端固定为 `4.0.14`：5.0.0 的 `Consumer` 是 class 61；[上游说明](https://pulsar.apache.org/blog/2026/10/05/announcing-apache-pulsar-5-0/)确认 4.0.x 客户端可支持 Java 8，4.0.14 的该类为 class 52，本线适配器目标测试 5/5 通过（2 项容器测试跳过）。固定项仍扫描漏洞。
- 1.0.x 的 Kafka 客户端固定为 `3.9.2`：Apache [3.9 兼容说明](https://kafka.apache.org/39/operations/java-version/)支持 JDK 8，[4.0 起移除 JDK 8](https://kafka.apache.org/43/getting-started/compatibility/)；候选 4.4.0 的 `AdminClient` 为 class 55，3.9.2 为 class 52。固定项仍扫描漏洞。
- 1.0.x 的 Artemis JMS 客户端固定为 `2.19.0`（`artemis.version`）：本线源码已注明 2.20.0 起要求 JDK 11；候选 2.57.0 的连接工厂是 class 61，JDK 8 无法编译 MQ 模块。固定项仍扫描漏洞。
- 1.0.x 的 Dozer Core 固定为 `6.5.2`（`dozer-core.version`）：候选 7.0.0 的 `Mapper` 类为 class 55，JDK 8 无法编译 Web MVC 模块；6.5.2 构件为 class 52。固定项仍扫描漏洞。
- 1.0.x 的 metrics 测试显式声明 `opentelemetry-common`：升级 OpenTelemetry 到 1.67.0 后，SDK 内部引用的 `ConfigUtil` 位于该构件；候选从 10 项测试异常恢复为 10 项通过，继续允许 OpenTelemetry 在本线自动升级。
- 1.0.x 的 Redis Testcontainers 扩展固定为 `2.0.2`（`testcontainers-redis.version`）：自动升级到 3.0.0 后测试使用的 `RedisContainer` 是 class 61，JDK 8 无法编译；2.0.2 构件为 class 52。固定项仍扫描漏洞。
- 1.0.x 的 OWASP Dependency-Check Maven 插件固定为 `10.0.4`：候选 13.0.0 在 JDK 8 reactor 中即使配置 `skip=true` 仍因插件要求 Java 11 而阻断构建。固定插件照常执行版本核查，依赖漏洞扫描另由每周 Trivy 门禁负责。
- 1.0.x 的 JSqlParser 固定为 `4.9`：这是[上游声明的最后一个 JDK 8 兼容版本](https://github.com/JSQLParser/JSqlParser)；候选 5.4 的类文件为 class 55，无法在 JDK 8 编译 MyBatis Plus 数据权限模块。固定项仍扫描漏洞。
- 1.0.x 的 R2DBC H2 主线与测试别名均固定为 `1.0.0.RELEASE`：1.1.0.RELEASE 调用新版 H2 的 `Session.prepareCommand(String)`，与本线 JDK 8 兼容的 H2 2.2.224 组合会在事件存储测试触发 `NoSuchMethodError`。固定项仍扫描漏洞。
- 1.0.x 的 Jdbi 固定为 `3.39.1`（`jdbi3.version`）：[Jdbi 官方兼容说明](https://github.com/jdbi/jdbi/blob/master/RELEASE_NOTES.md)将 3.39.x 定为最后支持 Java 8 的系列；候选 3.55.0 的 `Jdbi` class 61 无法在 JDK 8 编译。固定项仍扫描漏洞。
- 1.0.x 的 H2 固定为 `2.2.224`（`h2.version`）：自动升级到 2.5.252 后 JDK 8 的 JPA 测试无法加载 `org.h2.Driver`；固定项仍扫描漏洞。
- 1.0.x 的 Hibernate ORM 固定为 `5.6.15.Final`（`hibernate5.version`）：该线的 JPA 事件存储依赖 `javax.persistence`，升级到 Hibernate 7.4.13.Final 后目标测试编译读取 class 61 失败。固定项仍扫描漏洞。
- 1.0.x 的 `jakarta.persistence:jakarta.persistence-api` 排除自动升级：本线 Panache 实体使用 `javax.persistence`，模块显式依赖 2.2.3；升级到 3.2.0 后 `javax.persistence` 注解类缺失。排除项仍扫描漏洞。
- 三线的 Redisson 固定在 `3.52.0`（当前 3.x 最新版）：4.8.0 移除了缓存模块使用的 `RScript.ReturnType.STATUS/INTEGER`，缓存模块 48 项本地测试通过（4 项 Redis 容器测试跳过）。该固定项仍进入漏洞扫描。
- 三线将 Commons CLI `1.11.0`、Commons Codec `1.23.0`、Commons Collections `3.2.2`、Commons IO `2.22.0` 固定在当前可用的常规发行版。Versions 插件按数值排序时会误选 2003–2004 年的日期版，造成缺类和 JUnit 3.8.1 传递依赖；这四项仍进入每周漏洞扫描。签名工具已直接处理 null/空 Map，不再依赖旧 Collections 类的偶然传递引入。
- 3.0.x 将 BOM 中显式声明的 `io.vertx:vertx-core`、`vertx-junit5`、`vertx-web` 固定为最新 4.5.x 版本 `4.5.35`；Quarkus 3.40.1 的构建增强调用 Vert.x 4 的内部类，直接提升到 Vert.x 5.2.1 会缺类。Quarkus 模块候选打包测试通过，固定构件继续漏洞扫描。
- ClassGraph 固定为最新的 4.8.x 版本 `4.8.199`：6.0.0 的 class 69 要求 JDK 25；4.8.199 的关键类经 Maven Central JAR 核验为 class 52，三线均可使用。仍进入漏洞扫描。
- 三线的 Jedis 固定为最新 7.x `7.5.3`：8.x 移除了样例测试使用的 `JedisPooled`，7.5.3 JAR 保留该类且其字节码为 class 52。1.0.x 的 Helidon 服务发现构件固定为最新 1.4.x `1.4.19`（class 52）；自动升级到 Helidon 3.2.21 的 class 61 会破坏 JDK 8 编译。固定项照常扫描漏洞。
- `io.github.easy4j:httpconn`、`httputils`、`metrics-druid` 的源码分别位于 [httpconn](https://github.com/easy-4-java/httpconn)、[httputils](https://github.com/easy-4-java/httputils)、[metrics-druid](https://github.com/easy-4-java/metrics-druid)；各线使用对应的 `1.0.x.20260630-SNAPSHOT`、`2.0.x.20260630-SNAPSHOT`、`3.0.x.20260630-SNAPSHOT` 构件。
- `component-matrix.csv`：当前三线的有效版本与检查状态。
- `unverified-artifacts.csv`：尚未取得有效 JAR 检查结果的坐标；不等同于确认不存在。
- `validation-result.json`：严格门禁的原始结果；非零错误不能作为已完成处理。

## 复现

在每个 checkout 使用该线 JDK 与已有 `mvnw`，生成新的有效 POM：

```sh
./mvnw -N -f ddd4j-dependencies/pom.xml help:effective-pom -Doutput=/tmp/ddd4j-effective-8.xml
```

另外两线分别输出 `/tmp/ddd4j-effective-17.xml`、`/tmp/ddd4j-effective-21.xml`。从三个 checkout 的父目录运行：

```sh
python3 ddd4j/scripts/verify_dependency_alignment.py \
  --roots ddd4j ddd4j-v2.0.x ddd4j-v3.0.x \
  --effective /tmp/ddd4j-effective-8.xml /tmp/ddd4j-effective-17.xml /tmp/ddd4j-effective-21.xml \
  --policy ddd4j/config/dependencies/alignment-policy.json \
  --strict-artifacts --output /tmp/ddd4j-dependency-check
```

缺少本地 JAR 时，可在对应 checkout 使用已有 Maven 仓库配置执行
`dependency:get -Dartifact=groupId:artifactId:version -Dtransitive=false`，再复查。此次补充下载的公开 JAR 缓存在
`/tmp/ddd4j-dependency-alignment/jars`；可使用 `--extra-cache` 指向该目录。临时缓存不属于交付文件，也不会被提交。

门禁回归：

```sh
python3 -m unittest discover -s ddd4j/scripts -p test_dependency_alignment.py -v
```

依赖属性固定为三个分区：`Global Properties`、`Third-Party Dependencies`、
`Maven Dependencies`。每段按属性名进行大小写不敏感的自然字母排序。
`io.github.easy4j` 组件的版本属性不保留 `easy4j-` 前缀。发生名称冲突时，
删除同名的历史旧坐标和旧版本属性，只保留当前 `io.github.easy4j` 组件。

检查与重新格式化：

```sh
python3 ddd4j/scripts/verify_dependency_property_layout.py \
  ddd4j/ddd4j-dependencies/pom.xml \
  ddd4j-v2.0.x/ddd4j-dependencies/pom.xml \
  ddd4j-v3.0.x/ddd4j-dependencies/pom.xml

PYTHONPATH=ddd4j/scripts python3 ddd4j/scripts/format_dependency_properties.py \
  ddd4j/ddd4j-dependencies/pom.xml \
  ddd4j-v2.0.x/ddd4j-dependencies/pom.xml \
  ddd4j-v3.0.x/ddd4j-dependencies/pom.xml
```

## 状态的边界

- `bytecode-pass` 只证明检查到的基础 class 文件没有超过对应 JDK；不能证明传递依赖、反射、框架接口、自动配置或业务行为兼容。
- `replacement` 表示明确记录的组件坐标迁移或模块合并，不表示任意旧 SDK 与新 SDK 自动等价。
- `unsupported` / `manual-migration` 不计为支持，并使门禁失败。包括客观 JDK 限制、待验证的旧发行线，以及需要新框架适配产物的能力。
- `unverified-artifact` 不计为通过；严格模式下使门禁失败。可能是仓库缺失、错误版本、历史模块迁移或尚未下载。
- `absent` 主要表示后续 JDK 线引入的组件未在更低版本线提供，不能直接等同于业务能力缺失。
- `pom` 是上游真实发布的聚合 POM，不应当作可执行 JAR。

上游 BOM 间接导出的历史组件不机械求并集；本项目显式维护的组件以有效 POM 检查覆盖。所有 profile 都进入检查全集。测试分类器与主
JAR 分开处理。多版本 JAR 的 `META-INF/versions` 及 `module-info.class` 不作为基础 Java 版本超限证据。

Profile 枚举用于防止隐藏组件；各条件版本仍须在对应激活条件下生成 effective POM 并单独验证，默认模型不能证明所有条件分支运行兼容。

## 每周依赖审计

OFDRW 2.4.0 在三线管理 `org.ofdrw:ofdrw-core`。`ofdrw-full` 包含转换器，它声明 PDFBox 2.0.27，而三线 BOM 管理 PDFBox 3.0.7；完整转换能力须先做针对性集成测试，不能仅凭 Java 8 字节码将其列为兼容。


`.github/workflows/weekly-dependency-audit.yml` 每周一 UTC 02:17 检查 `feature/1.0.x`、`feature/2.0.x`、`feature/3.0.x` 各自的 BOM，并运行 Trivy 文件系统漏洞扫描。GitHub 的 `schedule` 只从默认分支加载工作流，因此需把该工作流交付到默认分支后才会自动运行；三个功能分支上的 `workflow_dispatch` 可供手动验证。

`config/dependencies/weekly-update-policy.json` 的 `fixed` 是逐线精确版本，配置版本漂移会失败；`excluded` 是有理由的不做最新版本比较的坐标，包括各线独立适配的 Spring、Jackson、Easy4J 和快照版本。规则以外的每个直接管理坐标都查询 Maven Central 最新稳定发布版（仅有预览版时取最新预览版），落后、元数据不可解析或查询失败都会使该线失败。报告保存在 `target/dependency-audit/version-report.json`；CI 不自动改写 POM，升级仍需按各线 JDK、框架、传递依赖和测试分别验证。

每周工作流先对目标分支执行启用单测的 Maven install，再由 CycloneDX 为 reactor 的每个非 POM 模块（含样例）生成解析后的依赖清单，包含 test/provided 作用域；缺少任一模块清单即失败。Trivy 无需 NVD 密钥，分别扫描合并后的 reactor 清单和目标分支 BOM 的全部直接管理版本，固定版本与排除升级的组件也参加漏洞扫描。两份扫描报告须逐项覆盖输入构件；达到分支策略 `cvss_threshold` 的漏洞、依赖解析失败或空报告均使任务失败。报告保存在本次工作流制品的 `baseline/`、`updated/` 目录中。版本新旧检查不能代替漏洞检测，扫描结果也不能证明升级后的运行兼容性。

本地运行：

```sh
python3 scripts/test_weekly_dependency_versions.py -q
python3 scripts/check_weekly_dependency_versions.py \
  --pom ddd4j-dependencies/pom.xml \
  --policy config/dependencies/weekly-update-policy.json \
  --line 1.0.x \
  --output target/dependency-audit/version-report.json
```
