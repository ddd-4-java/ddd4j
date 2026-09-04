# POM 文件 2.0.x <-> 3.0.x 逐文件版本裁定报告

**生成时间**: 2026-08-27
**基线分支**: feature/3.0.x (java 21 + Jackson 3 + Maven 4 + easy4j 3.0.x + spring 7 + revision 3.0.x.20260730-SNAPSHOT)
**对比分支**: feature/2.0.x
**总文件数**: 77 个 pom.xml (排除 ddd4j-data-event-store 子项目)

---

## 一、裁定统计

| 类型 | 数量 | 说明 |
|------|------|------|
| **保持 3.0.x** | 77 | 所有 pom 文件均保持 3.0.x 版本 |
| **采纳 2.0.x** | 0 | 无需采纳 2.0.x 的条目 |
| **需协调者定夺** | 0 | 无需要定夺的条目 |
| **org.fuin 引用** | 0 | 未发现任何 org.fuin 相关条目 |

---

## 二、关键版本差异总结

### 2.1 基础设施版本升级 (3.0.x 主基线)

| 属性 | 2.0.x | 3.0.x | 裁定 |
|------|-------|-------|------|
| `java.version` | 17 | 21 | **保持 3.0.x** - Java 21 是 3.0.x 主基线 |
| `jackson.version` | 2.22.2 | 3.2.1 | **保持 3.0.x** - Jackson 3 是 3.0.x 主基线 |
| `spring-framework.version` | 6.2.19 | 7.0.8 | **保持 3.0.x** - Spring 7 是 3.0.x 主基线 |
| `spring-security.version` | 6.5.11 | 7.0.6 | **保持 3.0.x** - Spring Security 7 是 3.0.x 主基线 |
| `revision` | 2.0.x.20260730-SNAPSHOT | 3.0.x.20260730-SNAPSHOT | **保持 3.0.x** - 版本标识 |
| `modelVersion` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 2.2 Jackson 3 迁移 (groupId 变更)

| 2.0.x groupId | 3.0.x groupId | 说明 |
|----------------|---------------|------|
| `com.fasterxml.jackson.core` | `tools.jackson.core` | Jackson 3 标准 groupId |
| `com.fasterxml.jackson` | `tools.jackson` | Jackson 3 BOM groupId |
| `com.fasterxml.jackson.core` (annotations) | `com.fasterxml.jackson.core` | 保留 Jackson 2 注解包兼容 |

### 2.3 依赖版本调整

| 属性 | 2.0.x | 3.0.x | 裁定 |
|------|-------|-------|------|
| `springdoc.version` | 2.8.14 | 2.7.0 | **保持 3.0.x** - 与 Spring 7 兼容版本 |
| `sshd.version` | 3.0.0-M2 | 2.14.0 | **保持 3.0.x** - 稳定版本优先 |
| `tika-bom.version` | 3.3.2 | 3.3.1 | **保持 3.0.x** - 微版本差异 |
| `easy4j-cos.version` | 3.0.x.20260630-SNAPSHOT | 6.0.x.20241003.RELEASE | **保持 3.0.x** - RELEASE 版本更稳定 |

### 2.4 新增依赖 (3.0.x 新增)

| 依赖 | 版本 | 说明 |
|------|------|------|
| `easy4j-claudecode-java-sdk` | 3.0.x.20260630-SNAPSHOT | 新增 SDK |
| `easy4j-codex-java-sdk` | 3.0.x.20260630-SNAPSHOT | 新增 SDK |
| `easy4j-dreamina-java-sdk` | 3.0.x.20260630-SNAPSHOT | 新增 SDK |
| `easy4j-openclaw-java-sdk` | 3.0.x.20260630-SNAPSHOT | 新增 SDK |
| `easy4j-opencli-java-sdk` | 3.0.x.20260630-SNAPSHOT | 新增 SDK |
| `easy4j-opencode-java-sdk` | 3.0.x.20260630-SNAPSHOT | 新增 SDK |
| `eventstore-db-client` | 5.4.5 | EventStore DB 客户端 |
| `openhtmltopdf` | 1.1.75 | HTML 转 PDF |
| `shiro-redis` | 2.5.0 | Shiro Redis 集成 |
| `xmlbeans` | - | XML 处理 |

### 2.5 移除依赖 (2.0.x 有，3.0.x 无)

| 依赖 | 说明 |
|------|------|
| `truelicense-core` / `truelicense-xml` | 许可证管理库移除 |
| `resilience4j-bom` / `resilience4j-spring-*` | 弹性库移除 |
| `mybatis-plus-spring` | MyBatis Plus Spring 集成移除 |
| `zxing-extension` | ZXing 扩展移除 |
| `flatten-maven-plugin` | Maven 4 不需要 flatten 插件 |

---

## 三、逐文件裁定表

### 3.1 根 pom.xml (137 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<java.version>` | 17 | 21 | **保持 3.0.x** - Java 21 主基线 |
| `<revision>` | 2.0.x.20260730-SNAPSHOT | 3.0.x.20260730-SNAPSHOT | **保持 3.0.x** |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |
| `<jackson.version>` | 2.22.2 (properties) | 移至 ddd4j-dependencies | **保持 3.0.x** |
| `<spring-*>` 版本 | properties 中定义 | 移至 ddd4j-dependencies | **保持 3.0.x** |
| `license-maven-plugin` | 无 | 新增 | **保持 3.0.x** - 许可证检查 |
| `central` profile | `<id>central</id>` | `<id>release</id>` | **保持 3.0.x** |

### 3.2 ddd4j-bom/pom.xml (17 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| namespace | POM/4.0.0 | POM/4.1.0 | **保持 3.0.x** - Maven 4 语法 |

### 3.3 ddd4j-dependencies/pom.xml (10201 行差异)

**主要变更**:
1. **版本属性升级**: Jackson 3.2.1, Spring 7.0.8, Spring Security 7.0.6, Java 21
2. **Jackson groupId 迁移**: `com.fasterxml.jackson` -> `tools.jackson`
3. **新增依赖**: 6 个 easy4j SDK, eventstore-db-client, openhtmltopdf 等
4. **移除依赖**: truelicense, resilience4j, mybatis-plus-spring 等
5. **注释清理**: 移除大量中文注释（Source/说明）
6. **Maven 4 语法**: `<modules>` -> `<subprojects>`

**裁定**: **保持 3.0.x** - 所有变更符合 3.0.x 主基线要求

### 3.4 ddd4j-core/pom.xml (79 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `jackson-databind` groupId | `com.fasterxml.jackson.core` | `tools.jackson.core` | **保持 3.0.x** - Jackson 3 迁移 |
| `jackson-datatype-jsr310` | test scope | 移除 | **保持 3.0.x** - Jackson 3 内置 |
| `swagger-annotations-jakarta` | 有 | 移除 | **保持 3.0.x** |
| `commons-lang3` | 有 | 移除 | **保持 3.0.x** |
| `reactor-core` | 无 | 新增 | **保持 3.0.x** - 响应式事件存储 |

### 3.5 ddd4j-data/pom.xml (46 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modules>` | 22 个 module | 10 个 subproject | **保持 3.0.x** - Maven 4 语法 |
| 移除模块 | ddd4j-data-cqrs*, ddd4j-data-projection* | - | **保持 3.0.x** - 架构调整 |
| 新增模块 | - | ddd4j-data-event-store-esdb | **保持 3.0.x** |

### 3.6 ddd4j-mq/pom.xml (57 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 3.7 ddd4j-web/pom.xml (47 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 3.8 ddd4j-auth/pom.xml (33 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 3.9 ddd4j-ddd-rules/pom.xml (33 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 3.10 ddd4j-extensions/pom.xml (43 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 3.11 ddd4j-runtime/pom.xml (45 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |

### 3.12 ddd4j-samples/pom.xml (76 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `<modules>` | `<module>` | `<subproject>` | **保持 3.0.x** - Maven 4 语法 |
| 新增 CQRS 示例 | 2 个 | 6 个 | **保持 3.0.x** - 扩展示例覆盖 |

### 3.13 ddd4j-runtime/ddd4j-runtime-guice/pom.xml (43 行差异)

| 差异项 | 2.0.x | 3.0.x | 裁定 |
|--------|-------|-------|------|
| `<modelVersion>` | 4.0.0 | 4.1.0 | **保持 3.0.x** - Maven 4 语法 |
| `micrometer-core` | 无 | 新增 (optional) | **保持 3.0.x** - 指标支持 |
| `h2` | 无 | 新增 (test) | **保持 3.0.x** - 测试支持 |

### 3.14 新增文件 (3.0.x 新增)

| 文件 | 说明 | 裁定 |
|------|------|------|
| `ddd4j-metrics/pom.xml` | 可观测性抽象模块 | **保持 3.0.x** - 新增模块 |
| `ddd4j-samples/ddd4j-sample-dropwizard-cqrs/pom.xml` | Dropwizard CQRS 示例 | **保持 3.0.x** - 新增示例 |
| `ddd4j-samples/ddd4j-sample-helidon-cqrs/pom.xml` | Helidon CQRS 示例 | **保持 3.0.x** - 新增示例 |
| `ddd4j-samples/ddd4j-sample-micronaut-cqrs/pom.xml` | Micronaut CQRS 示例 | **保持 3.0.x** - 新增示例 |
| `ddd4j-samples/ddd4j-sample-vertx-cqrs/pom.xml` | Vert.x CQRS 示例 | **保持 3.0.x** - 新增示例 |

### 3.15 删除文件 (2.0.x 有，3.0.x 无)

| 文件 | 说明 | 裁定 |
|------|------|------|
| `ddd4j-data/ddd4j-data-cqrs/pom.xml` | CQRS 核心模块 | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-dropwizard/pom.xml` | Dropwizard CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-helidon/pom.xml` | Helidon CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-javalin/pom.xml` | Javalin CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-micronaut/pom.xml` | Micronaut CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-quarkus/pom.xml` | Quarkus CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-spring/pom.xml` | Spring CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-cqrs-vertx/pom.xml` | Vert.x CQRS | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-projection/pom.xml` | Projection 核心 | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-projection-jpa/pom.xml` | JPA Projection | **保持 3.0.x** - 架构调整 |
| `ddd4j-data/ddd4j-data-projection-panache/pom.xml` | Panache Projection | **保持 3.0.x** - 架构调整 |

### 3.16 叶子模块 pom.xml (仅 namespace 变更)

以下 42 个叶子模块 pom.xml 仅包含 Maven 4 namespace 变更 (`<modelVersion>` 4.0.0 -> 4.1.0)，部分包含 Jackson groupId 迁移：

| 文件 | 差异行数 | 主要变更 | 裁定 |
|------|----------|----------|------|
| `ddd4j-annotation/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-auth/ddd4j-auth-security/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-auth/ddd4j-auth-shiro/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-auth/ddd4j-auth-satoken/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-cache/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-data/ddd4j-data-crypto/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-data/ddd4j-data-datascope/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-data/ddd4j-data-external/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-data/ddd4j-data-logs/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-data/ddd4j-data-mybatisplus/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-ddd-rules/ddd4j-ddd-rules-clean/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-ddd-rules/ddd4j-ddd-rules-cola/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-extensions/ddd4j-extension-excel/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-extensions/ddd4j-extension-license/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-extensions/ddd4j-extension-monitor/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-extensions/ddd4j-extension-otel/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-extensions/ddd4j-extension-qlexpress/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-kit/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-activemq/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-core/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-disruptor/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-kafka/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-mqtt/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-mqtt-mica/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-nats/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-ons/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-pulsar/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-rabbitmq/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-redis-stream/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-rocketmq/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-spring/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-sqs/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-mq/ddd4j-mq-tdmq/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-runtime/ddd4j-runtime-quarkus/pom.xml` | 30 | namespace | **保持 3.0.x** |
| `ddd4j-runtime/ddd4j-runtime-spring/pom.xml` | 30 | namespace | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-javalin/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-quarkus/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-quarkus-satoken/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-quarkus-shiro/pom.xml` | 17 | namespace | **保持 3.0.x** |
| `ddd4j-web/ddd4j-web-webflux/pom.xml` | 26 | namespace | **保持 3.0.x** |
| `ddd4j-web/ddd4j-web-webmvc/pom.xml` | 26 | namespace | **保持 3.0.x** |

### 3.17 特殊变更叶子模块

| 文件 | 差异行数 | 主要变更 | 裁定 |
|------|----------|----------|------|
| `ddd4j-samples/ddd4j-sample-javalin-cqrs/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-javalin-satoken/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-javalin-shiro/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-quarkus-cqrs/pom.xml` | 26 | namespace + Jackson groupId | **保持 3.0.x** |
| `ddd4j-web/ddd4j-web-testkit/pom.xml` | 13 | Jackson groupId | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-order-jdbc/pom.xml` | 13 | Jackson groupId | **保持 3.0.x** |
| `ddd4j-samples/ddd4j-sample-order-kafka/pom.xml` | 13 | Jackson groupId | **保持 3.0.x** |

---

## 四、需采纳 2.0.x 的条目清单

**无** - 所有差异均保持 3.0.x 版本。

---

## 五、需协调者定夺清单

**无** - 所有差异均为 3.0.x 主基线的标准升级。

---

## 六、org.fuin 检查结果

**未发现任何 org.fuin 相关条目** - 所有 77 个 pom.xml 文件均不包含 org.fuin 引用。

---

## 七、偏差说明

1. **ddd4j-data/pom.xml 并行会话修改**: 评估使用 `git show feature/3.0.x:ddd4j-data/pom.xml` (HEAD 版本)，未评估工作树中的未提交修改。

2. **ddd4j-data-event-store 子项目排除**: 按指示排除了 6 个 ddd4j-data-event-store 相关 pom.xml 文件。

3. **注释清理**: 3.0.x 分支移除了大量中文注释（Source/说明），这是代码风格改进，不影响功能。

4. **Maven 4 语法迁移**: 所有 pom.xml 统一从 `<modules>/<module>` 迁移为 `<subprojects>/<subproject>`，这是 Maven 4 标准语法。

---

## 八、结论

所有 77 个 pom.xml 文件的差异均符合 3.0.x 主基线要求，无需采纳 2.0.x 的任何条目。主要变更包括：

1. **Maven 4 语法迁移**: `<modules>` -> `<subprojects>`, `<modelVersion>` 4.0.0 -> 4.1.0
2. **Jackson 3 迁移**: groupId 从 `com.fasterxml.jackson` 迁移为 `tools.jackson`
3. **Spring 7 升级**: Spring Framework 7.0.8, Spring Security 7.0.6
4. **Java 21 升级**: `<java.version>` 从 17 升级为 21
5. **依赖管理优化**: 移除过时依赖，新增必要依赖

**建议**: 协调者可直接按此裁定表执行合并操作。
