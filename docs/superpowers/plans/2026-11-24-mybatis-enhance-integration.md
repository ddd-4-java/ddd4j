# MyBatis 双链实施任务

规格：../specs/2026-11-24-mybatis-enhance-integration.md

- [x] 第一批：权限回归、桥接、统一链、租户和 Guice 事务会话。
- [x] 第二批：上游 JSqlParser 兼容、原生 MyBatis 增强和数据库查询契约。
- [x] 第三批：数据库行为、缓存、签名、回滚、文档及兼容性验证。
- [x] 租户并发补充：Scope 创建线程校验，以及共享 delegate、线程池复用、TTL 异步捕获和多级传递的行为测试；完成目标测试和受影响模块回归。
- [x] 上游租户作用域补充：`mybatis-plus-enhance` 的 `TenantContext.Scope` 增加同等校验，验证 DDD4J 与增强组件两个入口在作用域关闭前后均拒绝跨线程关闭，并完成两仓回归。
- [x] MyBatis-Plus 加密接口清理：移除本地 `EncryptedFieldHandler`，策略桥接直接实现上游接口，更新既有契约测试与接入说明。Corretto 17.0.21 下执行 `./mvnw -o -pl ddd4j-data/ddd4j-data-mybatisplus,ddd4j-runtime/ddd4j-runtime-guice -am clean verify`，13 个 Maven 项目成功，938 项测试，0 失败、0 错误、0 跳过；Javadoc 与 JaCoCo report 成功。

- [x] 双模块字段加密后续清理：移除两个本地默认处理器、原生 MyBatis 的本地接口和两个 ORM 模块对 ddd4j-data-crypto 的依赖。JDK 17 下两个模块与 Guice 的 clean verify 共 14 个项目成功，947 项测试通过，无跳过。两个模块的测试类路径均无 ddd4j-data-crypto，原生 MyBatis 与 Plus 的 H2 用例均在不加载该包时通过加密、解密和篡改拒绝验证。

- [x] 原生 Mapper 与领域仓储分工：移除两个本地 Mapper 接口和未装配的权限 SPI，具体仓储显式绑定查询、计数、单删和批删。JDK 17 离线执行 `./mvnw -o -pl :ddd4j-data-mybatis -am clean verify`，703 项测试通过，无跳过；另用 javac 验证未绑定 SQL 操作的具体仓储无法编译，并确认产物中不含移除的三个类型。

- [x] 三线整合 MyBatis-Plus 数据权限与查询回填。组件三线各 166 项测试，DDD4J Guice 及依赖三线分别 911、940、922 项，均无失败、错误或跳过。Spring Boot 自动配置 3 项通过。校验了每条 DDD4J 线消费的匹配组件 JAR，以及三个 DDD4J JAR 均不含移除的本地拦截器。

只在实现及验证完成后勾选。

第三批完成证据：JDK 17 下 14 项 Maven 项目的 clean verify 成功，936 项测试零失败/错误/跳过；Boot 3 项及上游两组件 69/129 项独立 verify 通过。Javadoc 严格设置不变，JaCoCo 数据和报告生成；90% profile 保持原门槛，并以 50% 负例验证仍会阻断。默认 verify 不代表 90% 覆盖率通过，当前覆盖率及 JDK 21 文档构建边界见 ../../reports/2026-11-24-mybatis-enhance-integration.md。

## 租户并发首轮验证（2026-11-24，后续由上游统一校验）

- 首轮在 `Ddd4jTenantContext.Scope` 保存创建线程引用，只保护 `Ddd4jTenantContext.open()` 返回的作用域。该实现已由下述上游补充替代：归属校验统一放在上游，两个入口均受保护。
- 新增 `Ddd4jTenantContextConcurrencyTest` 共 7 项：非法跨线程关闭及拒绝后的恢复、已关闭作用域的跨线程拒绝、同线程幂等、4 线程各 50 轮重叠作用域、单工作线程正常/异常复用、TTL 队列提交时捕获（含无租户）、多级异步透传与逐层恢复。异步用例预启动工作线程，避免以父线程初始继承误判任务透传。
- 红灯：未实现校验时，两个跨线程关闭测试因未抛出 `IllegalStateException` 失败（异步断言由 `ExecutionException` 包装）；其余 5 项通过。补充校验后，新测试及既有租户/ThreadContext 目标回归共 20 项通过。
- Corretto 17.0.21 下执行 `./mvnw -o -pl ddd4j-data/ddd4j-data-mybatisplus,ddd4j-runtime/ddd4j-runtime-guice -am verify`：13 个 Maven 项目成功，935 项测试，0 失败、0 错误、0 跳过。其中 MyBatis-Plus 59 项（含 8 项真实 MySQL 集成测试）、Guice 117 项；Javadoc 和 JaCoCo report 执行成功。未启用可选 90% 覆盖率 profile，不将本次结果解释为该门槛通过。
- 本轮未提交、推送或发布。普通未包装线程池仅验证显式作用域的隔离和清理；异步自动透传的验证前提为 TTL 包装的执行器。

## 上游与双入口补充验证（2026-11-24）

- 上游 `TenantContext.Scope` 保存创建线程，在读取 `closed` 或恢复租户前校验线程身份。DDD4J 删除重复的校验字段和判断，保留静态薄包装并直接复用上游逻辑；两个入口的公共签名与同线程行为保持兼容。
- DDD4J 将关闭前跨线程拒绝、关闭后跨线程拒绝和同线程幂等测试参数化，同时覆盖 `context.open()` 与 `context.asEnhanceContext().open()`。原并发和 TTL 回归保留，该测试类现共 10 项。
- 上游新增 3 项用例：原租户为 null/非 null 时拒绝跨线程关闭且允许创建线程恢复，以及已关闭作用域仍拒绝其他线程。既有幂等测试增加关闭后再次设置租户的断言，确保重复关闭不会覆盖新值。
- 红灯证据：两仓临时 Maven reactor 中，DDD4J 的增强入口两个拒绝用例因未抛异常失败，DDD4J 原入口通过；上游独立测试新增的 3 项同样失败。补齐后，上游租户、DDD4J 双入口及原有上下文目标回归共 33 项通过。
- 上游使用 Corretto 17.0.21、已安装的 Maven 3.9.16 执行 `mvn -o install`：4 个 Maven 项目、162 项测试，0 失败/错误/跳过，产物仅安装到本机 Maven 缓存。扩展模块已安装 JAR 与本次构建 JAR 的 SHA-256 一致。上游自带 wrapper 缺失 `MavenWrapperMain`，未修改 wrapper 或安装新工具。
- 随后从 DDD4J 正常入口执行 `./mvnw -o -pl ddd4j-data/ddd4j-data-mybatisplus,ddd4j-runtime/ddd4j-runtime-guice -am verify`：13 个 Maven 项目、938 项测试，0 失败/错误/跳过。其中 MyBatis-Plus 62 项（含 8 项真实 MySQL 测试）、Guice 117 项；Javadoc 门禁和 JaCoCo report 执行成功。此次验证直接消费本机新安装的上游产物，没有使用临时 classpath 替代正常依赖解析。
- 上游整体 90% 覆盖率仍有原有告警（Core 47%、Extension 70%、Spring 19%，现有 `haltOnFailure=false` 未改）；未将构建成功当作整体覆盖率达标。两仓本次修改未提交、推送或远端发布，原有无关修改保留。

## 聚合仓储收敛（2026-11-25）

- [x] 添加错误 ID 编译负例和领域批量行为回归，记录红灯。
- [x] 公共聚合仓储组合 BaseMapper，保留 BaseRepositoryImpl 业务入口，删除重复实现并迁移 Guice。
- [x] 真实 MySQL 验证 Long/继承主键、批量写入/更新/upsert、写后读取和异常回滚；单元测试覆盖空集合与非法元素。
- [x] 受影响 clean verify、无 record 检查、迁移说明和最终产物核验。

最终结果：Corretto 17.0.21 下 13 个项目 `clean verify` 成功，953 项测试零失败、错误、跳过；其中 Plus 67 项（含 10 项 MySQL），Guice 127 项。保留 `BaseRepositoryImpl` 的原包名和五泛型入口，仅移除其重复泛型解析；两个入口均有 ID 编译正负例。无 record 检查及其 7 项自测通过，产物包含两个仓储类型。迁移说明见数据模块 README，详细证据见 `../../reports/2026-11-25-mybatis-repository-consolidation.md`。本次仅验证 2.0.x 本地结果，未提交、推送或远端发布。

## 注解及元数据驱动的主键修复（2026-11-25）

- [x] 添加原生 MyBatis 与 Plus 的真实 SQL 主键映射回归，记录红灯。
- [x] 复用现有主键元数据与 DomainObjectMapper，修复读取、查询翻译、条件更新/删除和回填。
- [x] 适配并验证 1.0.x / 2.0.x / 3.0.x，两模块、Guice 及依赖回归通过。
- [x] 检查无 record、提交范围、工作区保护并记录三线证据。

三线验证完成：JDK 8 / 17 / 21 下分别 1,308 / 975 / 1,308 项通过，无失败、错误、跳过；两模块分别 17 / 72 项。详细证据见 `../../reports/2026-11-25-mybatis-primary-key.md`。
