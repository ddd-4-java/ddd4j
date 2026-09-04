# Task 5.1+5.2 Report — ddd4j-data-event-store-panache（Quarkus Panache 实现）

**Status: DONE** · Commit `bf2ef34c`（feature/2.0.x，基于 856a92fe）· 门禁 BUILD SUCCESS

## 交付物

| 件 | 路径 | 说明 |
| --- | --- | --- |
| 模块 pom | `ddd4j-data/ddd4j-data-event-store-panache/pom.xml` | parent ddd4j-data；deps：ddd4j-data-event-store ${revision}、quarkus-hibernate-orm-panache（BOM 无版本）；test：quarkus-junit5、quarkus-jdbc-h2、jackson-datatype-jsr310（均无版本）；surefire 追加 `**/*IT.java` |
| 注册 | `ddd4j-data/pom.xml` +1 行 | 字母序：event-store-jpa 之后、external 之前 |
| 实体 | `.../panache/PanacheStoredEventEntity.java` | extends PanacheEntityBase；**公有字段风格（Panache 刻意约定，javadoc 已注明）**；@Id IDENTITY position；`ddd4j_stored_event` + `uk_aggregate_version` 同 -jpa；@Lob payload；静态 `findCurrentVersion`（order by version desc + firstResult，空安全 0L）与 `findByAggregate`（order by version） |
| 适配器 | `.../panache/PanacheEventStore.java` | @ApplicationScoped + 构造器 @Inject EventPayloadSerializer；append 标 **jakarta.transaction.Transactional**（Quarkus）→ findCurrentVersion → 冲突抛 AggregateVersionConflictException → 逐事件 version++ 公有字段赋值 → entity.persist()；read/read(from,to)/readAll；**两枚地雷已修**：`EventId.valueOf`（非 new EventId(String)——该构造器不存在）＋私有 record `StringAggregateRootId`（照 4.3 JpaEventStore 三方法） |
| ArchUnit | `.../panache/arch/EventStorePanacheModuleIndependenceTest.java` | 3 条 @ArchTest：`panache_impl_deps_allowlist`（io.ddd4j/java/jakarta/jackson 三件套/io.quarkus/org.hibernate/lombok）、`no_spring_in_panache_module`、`no_micronaut_in_panache_module` |
| IT | `.../panache/PanacheEventStoreIT.java` + `PanacheItCdiConfig.java` + `src/test/resources/application.properties` | @QuarkusTest；CDI 测试装配：@Produces 真实 EventPayloadSerializer（findAndAddModules mapper，零 mock）+ @Transactional clearStream（用例间隔离）；4 用例：①三事件追加读回（version 1/2/3、真实 Jackson 往返、correlationId/causationId 空安全）②乐观锁冲突（actualVersion=2、库中 2 条不变）③read(from,to) 闭区间 ④readAll 跨聚合 position 单调 + limit + fromPosition 含端点 |

## 门禁结果

`./mvnw -pl ddd4j-data/ddd4j-data-event-store-panache -am install` → **BUILD SUCCESS**

- 本模块 **7 tests, 0 failures**：ArchUnit 3（EventStorePanacheModuleIndependenceTest）+ IT 4（PanacheEventStoreIT）
- 上游 -am 链全绿：ddd4j-annotation(26) / ddd4j-kit(57) / ddd4j-core / ddd4j-data-event-store(15)
- 断言库告警清零（AssertJ `catchThrowableOfType` 已换新参序 (Class, ThrowingCallable)，旧参序在新版 AssertJ 已 @Deprecated）

## dependency:tree 实证（阶段 4 教训的强制项）

前置探针（/tmp scratch，仅 quarkus-bom 3.38.2 导入）：

| artifact | quarkus-bom 自带 |
| --- | --- |
| io.quarkus:quarkus-hibernate-orm-panache | 3.38.2 |
| org.hibernate.orm:hibernate-core | 7.4.5.Final |
| jakarta.persistence:jakarta.persistence-api | 3.2.0 |

真实模块 BOM 链（首次实测，未修前）——**混版实锤**：

| artifact | 解析版本 | 后果 |
| --- | --- | --- |
| hibernate-core | **7.2.6.Final**（父链更早 import 压过 quarkus-bom） | @QuarkusTest 启动即 `NoClassDefFoundError: org/hibernate/service/internal/ChangesetCoordinatorInitiator`（Quarkus 3.38 按 Hibernate 7.4 编译） |
| hibernate-graalvm / hibernate-jcache | **6.6.40.Final**（hibernate-platform:6.6.40 import） | 同上混版 |
| jakarta.persistence-api | 3.2.0 | 无冲突 |

**修复**：模块级 `dependencyManagement` 再导入 `io.quarkus.platform:quarkus-bom:${quarkus.version}`（=3.38.2，与 ddd4j-dependencies 同款）。模块级 import 优先于父链继承的管理项 → 本模块 Quarkus 全家桶整链对齐 quarkus-bom，**完全独立于 -jpa 模块的任何钉**（-jpa 的 6.6.40/3.1.0 钉是其模块局部，不影响本模块）。修复后实测：

| artifact | 最终解析版本 | scope |
| --- | --- | --- |
| io.quarkus:quarkus-hibernate-orm-panache | 3.38.2 | compile |
| io.quarkus:quarkus-hibernate-orm | 3.38.2 | compile |
| org.hibernate.orm:hibernate-core / -graalvm / -jcache | **7.4.5.Final** | compile |
| jakarta.persistence:jakarta.persistence-api | **3.2.0** | compile |
| com.fasterxml.jackson.core:jackson-databind（+jsr310） | **2.22.0**（quarkus-bom 管理，替代链上 2.21.2） | compile/test |
| io.quarkus:quarkus-jdbc-h2 → com.h2database:h2 | 3.38.2 → **2.4.240** | test |

（对照 -jpa 模块：hibernate 6.6.40.Final + jakarta.persistence 3.1.0——两模块各自独立解析、互不影响，符合任务指令。）

## 偏离与发现（自审）

1. **quarkus-bom 模块级再导入（新增，实测必要）**：即上表混版修复。阶段 4 教训的 Quarkus 侧镜像——不修则 IT 全挂；已写入 pom 注释留痕。
2. **application.properties 增补 3 项（偏离 brief 的 4 项清单）**：Quarkus 3.38 在 `devservices.enabled=false` 且无 URL 时将数据源 Bean 自动停用（实测 InactiveBeanException），故显式 `quarkus.datasource.jdbc.url=jdbc:h2:mem:ddd4j_event_store_it;DB_CLOSE_DELAY=-1` + username/password=sa/空。brief 的「H2 内存默认」在 3.38 已不成立。
3. **quarkus-junit5 → quarkus-junit 重定位告警**：3.31+ 官方改名，brief 钦定的 `quarkus-junit5` 经 relocation 正常解析（@QuarkusTest 类在 quarkus-junit jar 中，已实测）。构建有 WARNING，建议后续任务统一改用 `quarkus-junit` artifactId。
4. **Panache 静态泛型推断坑**：`PanacheStoredEventEntity.find(...)` 链式接 `this::toStoredEvent` 时 javac 将 `<T extends PanacheEntityBase>` 兜底推断为 PanacheEntityBase（编译错误）；以局部变量显式目标类型规避（代码内已注释）。计划 sketch 的 `firstResultLong("version")` API 不存在，`findCurrentVersion` 改用 order by version desc + firstResult() 等价实现（空安全 0L）。
5. **PanacheItCdiConfig（测试装配类，新增于 brief 未列）**：EventPayloadSerializer 无容器注解（ADR-0005 跨运行时纯类），@QuarkusTest 需 @Produces 注册真实 Bean——对齐 -jpa 模块 TestApp 的装配职责；同文件 @Transactional clearStream 承担用例间清表（-jpa IT 的 @BeforeEach deleteAll 对应物）。
6. 根 pom 无需改动（`ddd4j/pom.xml` 仅聚合到 `ddd4j-data`，嵌套模块不单列——与 -jpa 注册先例一致）。

## 遗留/风险

- 混版根源（父链更早 import 管理 hibernate-core 7.2.6 的具体 BOM）未做全库 forensics，属 ddd4j-dependencies 治理问题；本模块已用模块级 quarkus-bom 隔离。后续 panache 系模块（如 Task 5.x 的其它 Quarkus 适配）应复用本模块的再导入模式。
- jackson 2.21.2→2.22.0（本模块内）：EventPayloadSerializer/domain 事件序列化 IT 全绿，无兼容性问题实证。
