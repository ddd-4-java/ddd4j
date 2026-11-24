# ddd4j 示例工程

本目录提供七个运行时的 28 个订单示例，每个平台各有默认 DDD、CQRS、Sa-Token 和 Shiro 四个入口。Guice 配套 Javalin，Spring 提供 WebMVC 和 WebFlux 两种接入。样例复用共享 Order 业务，Spring 的四个入口沿用外部 `ddd4j-boot` 自动配置。

## 示例矩阵

聚合 POM 管理 7 个共享 Order 模块和 28 个独立样例。点选对应模块查看代码与运行说明。逐平台验收记录见任务报告，三线完整验收由 R14 记录。

| Runtime | Web 接入 | 默认 | CQRS | Sa-Token | Shiro | 任务 |
| --- | --- | --- | --- | --- | --- | --- |
| Spring | WebMVC、WebFlux | [默认](ddd4j-sample-spring/README.md) | [CQRS](ddd4j-sample-spring-cqrs/README.md) | [Sa-Token](ddd4j-sample-spring-satoken/README.md) | [Shiro](ddd4j-sample-spring-shiro/README.md) | S01 / R06 已验收 |
| Guice | Javalin | [默认](ddd4j-sample-javalin/README.md) | [CQRS](ddd4j-sample-javalin-cqrs/README.md) | [Sa-Token](ddd4j-sample-javalin-satoken/README.md) | [Shiro](ddd4j-sample-javalin-shiro/README.md) | S02 / R07 已验收 |
| Quarkus | Quarkus | [默认](ddd4j-sample-quarkus/README.md) | [CQRS](ddd4j-sample-quarkus-cqrs/README.md) | [Sa-Token](ddd4j-sample-quarkus-satoken/README.md) | [Shiro](ddd4j-sample-quarkus-shiro/README.md) | S03 / R08 已验收 |
| Micronaut | Micronaut | [默认](ddd4j-sample-micronaut/README.md) | [CQRS](ddd4j-sample-micronaut-cqrs/README.md) | [Sa-Token](ddd4j-sample-micronaut-satoken/README.md) | [Shiro](ddd4j-sample-micronaut-shiro/README.md) | S04 / R09 已验收 |
| Helidon | Helidon | [默认](ddd4j-sample-helidon/README.md) | [CQRS](ddd4j-sample-helidon-cqrs/README.md) | [Sa-Token](ddd4j-sample-helidon-satoken/README.md) | [Shiro](ddd4j-sample-helidon-shiro/README.md) | S05 / R10 已验收 |
| Vert.x | Vert.x | [默认](ddd4j-sample-vertx/README.md) | [CQRS](ddd4j-sample-vertx-cqrs/README.md) | [Sa-Token](ddd4j-sample-vertx-satoken/README.md) | [Shiro](ddd4j-sample-vertx-shiro/README.md) | S06 / R11 已验收 |
| Dropwizard | Dropwizard | [默认](ddd4j-sample-dropwizard/README.md) | [CQRS](ddd4j-sample-dropwizard-cqrs/README.md) | [Sa-Token](ddd4j-sample-dropwizard-satoken/README.md) | [Shiro](ddd4j-sample-dropwizard-shiro/README.md) | S07 / R12 已验收 |

表中的验收状态对应各平台任务。三条发布线的整体验收见 [R14 实施清单](../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#r14三线存储和多平台完整验收)。Java 8 的 Spring 默认示例已通过 WebMVC 和显式启用的 WebFlux HTTP 验收。直接调用旧 Easy4J ReactiveRequestContextHolder 的应用仍需兼容的外部构件，详见 [Spring 默认示例说明](ddd4j-sample-spring/README.md)。

## 启动默认订单示例

从仓库根目录执行命令，使用当前分支对应的 JDK 和 Maven Wrapper。七个默认入口都支持匿名创建和查询内存订单，启动无需配置数据库、消息队列或投影。CQRS、Sa-Token 和 Shiro 由上表各自的模块提供。

用 `-am` 从根目录打包时，上游模块显式启用的测试仍会执行，例如 MyBatis 的 MySQL 用例。已有本线依赖构件时，可以去掉 `-am`，仅打包所选样例。

### Spring 和 Quarkus

Quarkus 的默认包可直接启动。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus -am \
  -Dmaven.test.skip=true -Dmaven.javadoc.skip=true package
java -jar ddd4j-samples/ddd4j-sample-quarkus/target/quarkus-app/quarkus-run.jar
```

Spring 先按[本线依赖说明](ddd4j-sample-spring/README.md#源码依赖与构建)准备 Boot 装配依赖，再使用同一个 Maven 本地仓库打包。

```bash
./mvnw -Dmaven.repo.local="$DDD4J_MAVEN_REPO" \
  -pl ddd4j-samples/ddd4j-sample-spring -am \
  -Dmaven.test.skip=true -Dmaven.javadoc.skip=true package
```

若配置的 Maven 仓库已能解析本线 Boot 构件，可省略 `-Dmaven.repo.local` 参数。用 `java -jar` 运行该模块 `target` 中的 `ddd4j-sample-spring-<version>.jar`。默认内存模式使用 `demo-memory`。

### Javalin、Micronaut、Helidon、Vert.x 和 Dropwizard

这些入口使用本模块 JAR 和 Maven `runtime` 依赖。下面以 Vert.x 为例，将 `<version>` 换成构建产物的实际版本。

```bash
module=ddd4j-samples/ddd4j-sample-vertx
./mvnw -pl "$module" -am -Dmaven.test.skip=true -Dmaven.javadoc.skip=true \
  -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt \
  package dependency:build-classpath
java -cp "$module/target/ddd4j-sample-vertx-<version>.jar:$(cat "$module/target/runtime-classpath.txt")" io.ddd4j.sample.vertx.VertxOrderApplication
```

Vert.x 默认监听本机 8084。端口已占用时，可在 `java` 后添加 `-Dsample.port=0`，并从启动日志读取实际端口。用 `POST /api/orders` 创建订单，再用返回的 `data.id` 调用 `GET /api/orders/{id}`。

切换样例时换用对应目录、JAR 文件名和下表的主类。

| 默认样例 | 启动主类或产物 | 默认端口 | 订单路径 |
| --- | --- | --- | --- |
| Spring | `ddd4j-sample-spring-<version>.jar` | `8080` | `/orders` |
| Guice/Javalin | `io.ddd4j.sample.javalin.JavalinSample` | `7000` | `/api/orders` |
| Quarkus | `target/quarkus-app/quarkus-run.jar` | `8080` | `/api/orders` |
| Micronaut | `io.ddd4j.sample.micronaut.MicronautOrderApplication` | `8083` | `/api/orders` |
| Helidon | `io.ddd4j.sample.helidon.HelidonOrderApplication` | `8080` | `/api/orders` |
| Vert.x | `io.ddd4j.sample.vertx.VertxOrderApplication` | `8084` | `/api/orders` |
| Dropwizard | `io.ddd4j.sample.dropwizard.DropwizardOrderApplication` | `8086` | `/api/orders` |

Dropwizard 在主类名后加 `server ddd4j-samples/ddd4j-sample-dropwizard/src/main/resources/dropwizard-order.yml`。

## 普通 DDD 示例

七个框架的默认样例使用同一组 Order 业务概念，可对照各框架的装配和调用。

| 业务轨道      | 示例对象                                            | 设计重点                                                                     |
|---------------|-----------------------------------------------------|------------------------------------------------------------------------------|
| 充血聚合      | `Order` / `OrderLine` / `Money` / `OrderRepository` | 聚合根封装状态机、不变量和领域事件，领域层不依赖具体 Web/DI/ORM              |
| 轻量 PO/Query | `Goods` / `GoodsQuery` / `GoodsRepository`          | 简单 CRUD 场景保留轻量数据对象，查询能力通过 `Query` + `RichRepository` 承接 |

推荐先阅读：

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus -am test -DskipTests=false
./mvnw -pl ddd4j-samples/ddd4j-sample-javalin -am test -DskipTests=false
./mvnw -pl ddd4j-samples/ddd4j-sample-micronaut -am test -DskipTests=false
./mvnw -pl ddd4j-samples/ddd4j-sample-vertx -am test -DskipTests=false
./mvnw -pl ddd4j-samples/ddd4j-sample-helidon -am test -DskipTests=false
./mvnw -pl ddd4j-samples/ddd4j-sample-dropwizard -am test -DskipTests=false
```

Order JDBC 样例提供 `ReliableOrderOutboxPublisher` 作为可靠 Outbox 发布入口：`JdbcMQOutboxStore`
短事务领取，`MQOutboxDispatcher` 在事务外调用 `IntegrationEventPublisher`，再条件确认。
`JdbcOutboxPort.findById` 重建完整 `OutboxMessage`，因此 Kafka 的聚合 ID 键和事件封装沿用原格式。
订单 JDBC 模块的 `JdbcOrderSampleRuntime.start(...)` 装配共享 JDBC 事务和订单服务，并启动可靠 Outbox 工作器。显式启用 JDBC 的样例在应用关闭时调用 `close()` 停止工作器。调用方管理传入的数据源、幂等端口和消息发布器。默认 HTTP 入口使用内存。也可以直接接入 `ReliableOrderOutboxWorker`。工作器在业务事务外定期调用 `publishPending(limit)`，该入口拒绝在订单事务中执行。每个运行实例使用稳定且唯一的 `leaseOwner`。
旧 `TransactionalOutboxPublisher` 保留供兼容调用，但其 broker 发送期间持有数据库事务和行锁。

## CQRS 示例

从上表选择 CQRS 模块，按对应 README 运行。

- Spring 和 Vert.x 的 CQRS 主入口需要专用 PostgreSQL 样例库。
- Javalin、Micronaut、Helidon、Dropwizard 提供默认内存流程，可按模块说明启用 PostgreSQL 持久路径。
- Quarkus 的 Java 8 分支默认运行内存流程，使用 `durable-order` 和 `durable` profile 构建持久入口。Java 17 和 21 的 CQRS 构件启用 PostgreSQL。

持久模式仍使用现有 Order JDBC 迁移和 JPA 事件、检查点表。接口路径见各模块说明。持久示例将命令写入和读模型查询拆开，可观察投影前后的读取变化。

三条线的当前源码和构建清单已移除 12 个独立投影构件。公共处理器注册和事件分发由 `ddd4j-core` 提供，各平台通过对应的 `ddd4j-runtime-*` 接入投影。需要持久化检查点时，按相应样例的 README 选择存储适配。剩余旧 CQRS 构件的退出条件见 [R15 实施清单](../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#r15退出旧实现模块并保持可回退交付)。

S01—S07 记录了分批持久化、失败重试和应用重开结果。当前三条发布线的整体验收见 [R14 实施清单](../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#r14三线存储和多平台完整验收)。

## Auth 示例

在上表选一个框架的 Sa-Token 或 Shiro 模块。Spring 和 Quarkus 使用各自的可执行包，其余框架使用模块 JAR 与 Maven 运行时依赖。启动、登录和权限示例见对应模块的 README。

业务层使用 ddd4j 的 `Subject`、`SubjectProvider` 和 `AuthRequest` 契约。各鉴权引擎的会话和权限配置由对应样例和适配层提供。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-micronaut-satoken -am test
```

## 当前约束

- 模块清单以 `ddd4j-samples/pom.xml` 为准。Java 8 和 17 使用 Maven 3 的 `modules`，Java 21 使用 Maven 4 的 `subprojects`。
- Spring 的业务入口位于本目录，Boot 自动配置由外部 `ddd4j-boot` 仓库维护。
- Quarkus/Javalin 专属脚手架能力分别归外部 `ddd4j-quarkus`、`ddd4j-javalin`，本目录只保留可对照的业务样例。

## 历史验收记录

已有默认模块命名为 `ddd4j-sample-<framework>`，其他三类带 `-cqrs`、`-satoken`、`-shiro` 后缀；Spring四项见[运行说明](ddd4j-sample-spring/README.md)，本批 S01 已通过独立审查。共享 order-* 不计入28。S01 在 Java 17 验证了 Spring 默认例的 WebMVC 和 WebFlux。两类认证例的 HTTP 证据覆盖 WebMVC。Javalin四项已通过S02审查：默认简单入口、持久CQRS、两原生认证及运行时依赖下的打包启动/关闭均有证据。认证按实际匹配路由判权，保留普通及尾斜杠URL。Quarkus四项已通过S03审查：默认沿用`/api/orders`且不强制Bearer，CQRS有独立持久查询与同库重开，两原生认证有真实请求完成清理。Shiro保留匿名`GET /orders`公开列表，其余受保护订单操作按原生权限控制。Micronaut运行时R09与四类样例S04均已通过审查：默认例支持直接业务调用，CQRS已验证持久失败恢复及数据库容器/应用重开，两认证例验证原生权限与线程传播清理；Helidon四入口S05也已通过审查，当前10项样例测试与46项实际main HTTP通过，持久CQRS有数据库容器/应用同库重开证据。两原生认证的聚合结束观察通过，断连请求的精确完成关联留R14强化。Vert.x运行时R11与四类样例S06已通过独立复审：真实持久投影和原生认证完成，应用等待已进入worker及定时投影后关闭资源。Dropwizard四类样例S07已完成本地实现与验证：26项样例测试、36次生产JAR/main HTTP和同库独立JVM恢复；独立审查已通过（C0/I0/M3），验证边界见任务报告。

共同验收：default不强制bus/ES/projection/MQ/auth；CQRS覆盖读写链和失败恢复；satoken/shiro各覆盖登录、允许/拒绝、退出失效、异常及请求结束后上下文清理。所有入口保留普通业务调用，真实平台启动与业务测试结果进入R14/R15完整28场景门禁。详见[活动计划](../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#architecture-convergence-tasks-20261118)。

### S04 Micronaut 四入口

`ddd4j-sample-micronaut`（`/api/orders` 默认无强制认证）、`ddd4j-sample-micronaut-cqrs`（显式 PostgreSQL/JPA 的 `/sample/orders`，保留内存 `/orders`）、`ddd4j-sample-micronaut-satoken`、`ddd4j-sample-micronaut-shiro` 均使用原生 Micronaut main、编译期 BeanDefinition 及共享 Order。各模块 README 说明实际端点、原生认证和事务/线程边界。S04 工程验证见 `.superpowers/sdd/2026-10-10-three-line-product-convergence/task-S04-report.md`；独立审查状态由控制器确定。

### S05 Helidon 四入口

Helidon default/cqrs/satoken/shiro 使用真实 MP/CDI/JAX-RS 主入口，复用共享 Order。default 保留 `/api/orders` 和显式 REQUIRED Bearer/支付幂等契约；CQRS 保留 `/orders` 内存 DTO，通过独立 `/sample/orders` 验证 PostgreSQL、R10 Runner、失败恢复和重开；两认证入口使用原生会话/权限及同步生命周期。各模块 README 说明真实边界，工程证据位于 `.superpowers/sdd/2026-10-10-three-line-product-convergence/task-S05-report.md`，独立接受由控制器确认。


### S06 Vert.x 四入口

Vert.x default/cqrs/satoken/shiro 均提供 native main 与同源 callable lifecycle。default 主入口无强制认证并保留原 REQUIRED router；CQRS 主入口在既有 `/orders` 五字段契约下复用共享 Order/JDBC/Outbox、R11 源感知 Runner 与持久查询；两认证入口使用真实 Sa-Token/Shiro worker 作用域。详见四模块 README 和 `.superpowers/sdd/2026-10-10-three-line-product-convergence/task-S06-report.md`；S06已通过独立审查及Fix1复审（C0/I0/M0）。关闭先停止准入和后续定时任务，等待已进入的业务/完整投影，再释放资源；初轮1,329项与修复后受影响32项分时记录，详见报告。

R13 验收时，用户确认没有业务项目使用当时清单中的 14 个旧 CQRS 和平台投影坐标。样例和独立消费者验证了当时保留的构件、API、配置及数据格式，覆盖范围为样例和兼容性测试。现行构件状态见 [R15 实施清单](../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#r15退出旧实现模块并保持可回退交付)。

### Dropwizard 四入口（S07）

- [default](ddd4j-sample-dropwizard/README.md)：共享 Order 直接业务，默认无需认证，保留显式 Bearer。
- [cqrs](ddd4j-sample-dropwizard-cqrs/README.md)：旧 memory API 与独立持久 `/sample/orders`，原生 Bundle/Managed。
- [satoken](ddd4j-sample-dropwizard-satoken/README.md)：原生 Sa-Token 凭证、权限、会话及请求作用域。
- [shiro](ddd4j-sample-dropwizard-shiro/README.md)：原生 Shiro Realm、权限、会话及请求作用域。


### 2026-11-18 R13 兼容验收

当时 Java 17 的旧、新、混合及单旧坐标构成的 35 个独立消费者均通过构建。验收覆盖冻结的旧客户端、当时的原生装配和七平台旧实现的续写与回退。独立审查 C0/I0/M2，核验了产物来源和冻结输入。当时共有七个 runtime 实现，另有一个 runtime-testkit。随后 R14 在投影坐标退役前完成了三线独立消费者复验。真实后端矩阵和远端 CI 的当前状态见 R14。此次历史验收详见活动计划 R13、`task-R13-report.md`、`task-R13-review.md` 和 `R13-evidence/controller-verification.json`。
