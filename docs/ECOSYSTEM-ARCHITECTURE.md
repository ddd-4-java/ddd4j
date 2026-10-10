# ddd4j 生态架构分析（架构师视角）

> 基于 codegraph 索引与源码核对，2026-10-06。
> 索引统计：ddd4j 1,454 文件 / 30,228 节点 / 59,282 边；ddd4j-boot (4.0.x) 918 文件；ddd4j-cloud (2025.1.x) 416 文件。

## 一、生态总览与版本矩阵核对

| 项目          | 本地检出分支 | revision                | JDK（实测 pom）                                  | 基座版本                                                        | 与矩阵一致？                 |
|:--------------|:-------------|:------------------------|:-------------------------------------------------|:----------------------------------------------------------------|:-----------------------------|
| ddd4j         | (1.0.x 主线) | 1.0.x.20260630-SNAPSHOT | **1.8**（ddd4j-dependencies `java.version=1.8`） | —                                                               | ✓                           |
| ddd4j-boot    | **4.0.x**    | 4.0.x.20260630-SNAPSHOT | **21**                                           | Spring Boot **4.0.8**                                           | ✓（矩阵：2025.1.x ↔ 4.0.8） |
| ddd4j-cloud   | **2025.1.x** | cloud.revision          | 继承 boot 21                                     | parent=boot 4.0.x.20260630；spring-cloud-alibaba **2025.1.0.0** | ✓（2025.1.x ↔ 4.0.x）       |
| ddd4j-javalin | **7.2.x**    | 7.2.x.20260630-SNAPSHOT | **21**                                           | Javalin 7.2.3                                                   | ✓                           |
| ddd4j-ai      | **2.0.x**    | 2.0.x.20260630-SNAPSHOT | **17（实测）**                                   | spring-ai 2.0.0                                                 | **偏差**：矩阵写该线 JDK 21  |
| ddd4j-quarkus | **4.0.x**    | io.ddd4j.quarkus        | **21**                                           | Quarkus（3.38.2 线）                                            | ✓                           |
| ddd4j-web3    | **2.0.x**    | 2.0.x.20260630-SNAPSHOT | **21（实测）**                                   | web3j 2.7.0 / ton4j 2.1.0                                       | **偏差**：矩阵写该线 JDK 17  |

**三处偏差建议**：`ddd4j-ai` 2.0.x 与 `ddd4j-web3` 2.0.x 的 `java.version`
与既定矩阵互为颠倒，需在对应分支上复核修正（疑为复制模板时串线）。修正前，下游项目按矩阵选线时应以"目标运行时 JDK"实测为准。

**Maven 4 演进规则核对**：ddd4j 3.0.x / boot 4.0.x / 4.1.x / cloud 2025.1.x / quarkus 4.0.x / javalin 7.2.x / ai 2.0.x
均应使用 `<subprojects>`。实测：boot 4.0.x ✓、cloud 2025.1.x ✓、ai 2.0.x ✓ 已改；javalin 7.2.x / quarkus 4.0.x / web3 2.0.x
线需逐分支核对（web3 2.0.x 按矩阵属 ddd4j 2.0.x 线，可保留 `<modules>`）。

## 二、ddd4j（基座，JDK 8 基线）

16 个顶层模块：
`bom / dependencies / annotation / core / kit / ddd-rules / data(32) / metrics / mq(16) / web(12) / auth(7) / cache / runtime(9) / extensions(9) / samples / parent`。

架构师要点：

- **core 是纯领域骨架**：`io.ddd4j.core` 下
  `cqrs / ddd(event, model, repository) / enums / exception / health / i18n / subject / util`。`ApiRestResponse`、
  `DomainEvent`、聚合与仓储抽象都从这里出发，无 Spring 依赖——这是"框架差异限制在边界层"能成立的前提。
- **ddd-rules 是架构守护**：`ddd4j-ddd-rules-clean` 提供 `CleanArchitectureChecker` 与 `CleanDDDLayerRules`
  ，把分层规则写成可执行检查（对应 oss 规格中"分层约束测试"的同类手法）。
- **data/mq/web/auth/runtime 是能力聚合层**：内部按 core/框架/厂商继续平铺（如 data 下 32 个子模块按存储介质拆分），单一聚合模块不引入运行时代码。
- **JDK 8 基线的代价**：依赖版本管理（`ddd4j-dependencies`）必须为全部三方选择 8 兼容版本（如 hibernate 5.x 系），这是
  2.0.x/3.0.x 线存在的主因。

## 三、ddd4j-boot（Spring Boot 基座，13 分支 2.3.x→4.1.x）

- 版本矩阵：`2.3.x(2.3.12.RELEASE) … 4.1.x(4.1.0)`；1.0.x 线五分支（2.3~2.7）、2.0.x 线六分支（3.0~3.5）、3.0.x 线两分支（4.0/4.1）。
- 当前检出 4.0.x：`<subprojects>` Maven 4、`java.version=21`、Spring Boot 4.0.8、spring-boot-admin 4.0.4。
- 与 ddd4j 的对应： **每个 boot 分支的 `ddd4j-boot-dependencies` 锁定对应 ddd4j 线的 easy4j 组件版本**（2.0.x 线引用
  easy4j 2.0.x.20260630，3.0.x 线引用 3.0.x.20260630），这是"基座换线、组件同线"的单一事实源。
- 模块职责与 ddd4j 平行：core（api 面：`io.ddd4j.boot.api`）/ cache / web (webmvc/webflux) / data (mybatis/jpa/external) /
  auth (satoken/shiro/security) / mq / extensions / parent / samples。

## 四、ddd4j-cloud（Spring Cloud 基座，8 train）

- 8 train 矩阵：Hoxton.SR12 ↔ 2.3.x、2020.0.6 ↔ 2.4.x、2021.0.9 ↔ 2.7.x、2022.0.5 ↔ 3.0.x、2023.0.6 ↔ 3.2.x、2024.0.3 ↔
  3.4.x、2025.0.3 ↔ 3.5.x、2025.1.3 ↔ 4.0.x。
- 当前检出 2025.1.x：六个子模块（bom/dependencies/docs/extensions/parent/samples），parent 继承 boot 4.0.x ✓。
- **对 ddd4j-oss 的直接影响**：oss 声明的 `ddd4j-cloud.version=2023.0.x.20251205-SNAPSHOT` 按本矩阵对应 boot 3.2.x，而 oss
  在 boot 3.4.x（2024.0.x train）上—— **本地供给 BOM 应改导入 Spring Cloud 2024.0.3**（下游 oss 的修正项，见第七节）。

## 五、ddd4j-javalin / quarkus / ai / web3（技术栈变体）

- **javalin 7.2.x**（JDK21，13 子模块）：唯一已内置 testcontainers 支持的变体——`ddd4j-javalin-testcontainers` 提供
  `AbstractTestContainerFixture` + Keycloak/LocalStack/MySQL/MariaDB/Mongo/Postgres/Redis 七类 Fixture，
  `testcontainers.version=2.0.5`。 **这是全生态 testcontainers 测试的样板模块**。
- **quarkus 4.0.x**（JDK21）：性能云原生变体，native 编译导向。
- **ai 2.0.x**：spring-ai 2.0.0（JDK 待修正为 21）。
- **web3 2.0.x**：web3j + ton4j 双链（JDK 待修正为 17）。

## 六、testcontainers 模块映射建议（依据 testcontainers.com/modules/）

| 基座/模块           | TC 官方/社区模块                                                           | 落点建议                                                                       |
|:--------------------|:---------------------------------------------------------------------------|:-------------------------------------------------------------------------------|
| ddd4j-boot data     | `mysql`、`mariadb`、`postgres`、`mongodb`、`cassandra`、`clickhouse`       | 以 javalin-testcontainers 的 database Fixture 为模板移植到各 boot 分支 samples |
| ddd4j-boot cache/mq | `redis`、`kafka`、`rabbitmq`、`nats`、`pulsar`                             | 对应 mq 16 子模块逐个补 IT                                                     |
| ddd4j-boot auth     | `keycloak`（TC 官方）、`vault`                                             | auth-satoken/auth-security 集成测试                                            |
| ddd4j-cloud         | `nacos`（社区 `nacos-testcontainers`）、`localstack`、`consul`             | cloud-extensions 注册配置中心 IT                                               |
| 对象存储（oss）     | **`MinIO`（testcontainers 官方一等模块，已核对 modules 目录 2026-10-06）** | oss 扩展契约测试：`MinIOContainer` 起真实 S3 端点跑 MultipartOperations 契约   |
| AI                  | `ollama`（TC 官方）、`qdrant`、`milvus`、`pgvector`（经 postgres 模块）    | ddd4j-ai 2.0.x 向量/模型 IT                                                    |
| Web3                | 无一等 TC 模块；用 `ganache`/`anvil` GenericContainer                      | web3 合约交互 IT                                                               |

官方目录同时确认：MySQL/MariaDB/Postgres/MongoDB/Redis/Kafka/RabbitMQ/NATS/Pulsar/Cassandra/ClickHouse/Elasticsearch/Keycloak/LocalStack/Vault/Ollama/Qdrant/Milvus
均为一等模块；Nacos 为社区模块（nacos-testcontainers）。

代码骨架（与 javalin 样板同构）：

```java
public class MySqlTestContainerFixture extends AbstractTestContainerFixture {
    @SuppressWarnings("resource")
    public MySqlTestContainerFixture() {
        container = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("ddd4j").withUsername("test").withPassword("test")
                .withInitScript("sql/V2_0_0__init.sql");
    }
}
// 用例：@Testcontainers + @Container 注入 DynamicPropertySource 覆盖 spring.datasource.*
```

## 七、对在途项目（ddd4j-oss）的修正清单

1. 本地供给 cloud BOM：spring-cloud 由 2023.0.3 改 **2024.0.3**（2024.0.x train ↔ boot 3.4.x），alibaba 改 **2023.0.3.2 → 依
   2024 train 的兼容版**（或升级 spring-cloud-alibaba 2023.0.3.3）。
2. easy4j 组件：oss 在 boot 3.4.x（ddd4j 2.0.x 线）上，easy4j 组件应统一 **2.0.x.20260630-SNAPSHOT**（当前 oss 用 1.0.x 线
   hitool 属 JDK8 产物，需在 2.0.x 线重验）。
3. oss 的 web3/ai 式 JDK 复核同样适用：boot 3.4.x = JDK 17，oss 构建 JDK17 ✓。

## 八、结论

生态的骨架是"**一个纯领域基座（ddd4j）+ 多运行时基座（boot/cloud/quarkus/javalin）+ 垂直变体（ai/web3）**"，靠 revision
版本线（1.0.x/2.0.x/3.0.x.20260630）与 `dependencies` BOM 做同线锁定。架构师的三个抓手： **ddd-rules 的可执行分层检查**、
**dependencies 单一版本事实源**、 **javalin-testcontainers 的 Fixture 样板**——分别对应架构守护、依赖治理、集成验证三件事。
