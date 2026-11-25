# MyBatis 双链增强集成

状态：实施中。事实源为本文；执行任务见同日期 plan。

## 目标
DDD4J 2.0.x 对接 io.github.easy4j 两套增强组件，保持 core 无 ORM/Spring 依赖，保留现有公共适配接口。两种数据模块在应用中二选一。

两个 ORM 模块各自直接使用增强组件的 EncryptedFieldHandler 和默认实现，通过 encryption(handler) 显式装配。移除本地同名接口、默认实现及对 ddd4j-data-crypto 的依赖。

本次租户作用域线程归属变更以本文为统一规格事实源：DDD4J 仓库负责适配入口和双入口回归，`workspace-github-easy-4-java/mybatis-plus-enhance` 仓库负责 `TenantContext.Scope` 的公共校验实现及独立测试，不初始化第二套规格体系。

两个互斥的 ORM 模块使用相同根包和同职责类型名：配置入口为 config.Ddd4jMybatisConfigurer，SQL 观测类型位于 plugins.observation，租户上下文位于 context，仓储位于 repository 和 repository.impl。对应测试按生产职责分包。

权限与回填的三线整合使用同一规格。mybatis-plus-enhance 提供 SQL 权限条件适配和查询结果回调，官方 MyBatis-Plus 插件负责权限改写。DDD4J 装配公共插件并以普通回调识别 Query 与聚合结果。三线一致启用 SELECT 与受支持的单表 UPDATE/DELETE 数据范围，权限先于分页，解析失败终止。查询成功时按增强链或独立插件入口执行一次回调，配置式和直接注册均有回归。两种权限入口互斥。

## 验收
- 权限条件与业务 OR 条件正确组合；SQL 解析失败不得静默绕过权限。
- 配置租户能力后，按 ID、Mapper/XML、查询及写入均受租户隔离；忽略表必须显式声明。
- `Ddd4jTenantContext.Scope` 和上游 `io.github.easy4j:mybatis-plus-enhance-extension` 的 `TenantContext.Scope` 只能由创建线程关闭；跨线程关闭（包括已关闭的作用域）须在改变任何上下文前抛出 `IllegalStateException`，拒绝后创建线程仍可恢复原租户。同线程重复关闭保持幂等。`Ddd4jTenantContext.open()` 与 `asEnhanceContext().open()` 两个入口执行相同契约，不能通过增强组件入口绕过检查。
- 同一 `Ddd4jTenantContext` 及其增强组件适配器可供多线程共享；并发作用域和线程池复用不得串租户，正常或异常结束均恢复原租户。异步透传通过 TTL 包装的执行器验证提交时捕获、多级传递及工作线程原上下文恢复，不承诺未经包装的线程池自动透传。
- 两个 ORM 模块在没有 ddd4j-data-crypto 的应用中使用各自的上游处理器；写密文、读明文，验签失败可观察，缓存重复读取不重复解密。
- SQL 观测来自执行链，成功/失败均发布；原 DDD4J Sink 接口保持兼容。
- 统一增强链由中立工厂构造，运行时复用，避免重复外层插件。
- Guice Mapper 使用受管理会话，事务成功提交、异常回滚、结束释放；禁止跨线程共享裸 SqlSession。
- 原生 Mapper 直接使用上游 EnhanceMapper。具体聚合仓储必须在编译期实现选择记录、计数、单删、批删四项 SQL 绑定。分页与计数使用同一过滤语义，不支持的查询条件在业务绑定中明确拒绝。原生模块移除两个本地 Mapper 接口及未接入执行链的 DataScopeProvider。
- mybatis-enhance SQL AST 与 DDD4J JSqlParser 5.3 兼容；不强制降级整个应用。
- 使用真实 SqlSession/数据库验证两条链；记录数据库类型、跳过和失败，不以编译代替验收。

## 边界
按当前 2.0.x 开发，不提交、推送、发布，不改数据库；i18n、INSERT IGNORE、TypeHandler 按需开放，不默认开启。

## 聚合仓储收敛（2026-11-25）

用户授权修复仓储泛型、批量接口与重复实现，并明确保留 `BaseRepositoryImpl` 作为业务仓储继承入口。公共实现统一在 `MybatisAggregateRepository`，入口类不重复解析泛型或覆盖 CRUD；`MybatisAggregateRepository` 组合 `BaseMapper<P>`，只实现领域仓储和模型映射契约，原生 PO 操作直接调用 Mapper。原生 MyBatis 模块及其他版本线不在此项范围内。

- `M extends AggregateRoot<ID>`；错误的聚合/仓储 ID 组合必须在编译期被拒绝。删除绕过泛型的旧 get/list 及同义入口。
- 领域 `saveBatch`、`updateBatchById`、`insertOrUpdateBatch` 调用组件已有集合 API；支持 Long、String 和继承声明的主键，不手写主键反射或批量 SQL 执行器。
- 保存批量执行插入；更新批量只更新，缺失记录计 0；upsert 支持新增与更新。保留 DDD4J 填充和持久化字段回写；空集合返回空/0，非法元素在写入前拒绝。
- 返回计数来自 JDBC BatchResult，SUCCESS_NO_INFO 按成功的一条语句计数，失败标记抛异常。MyBatis-Plus 的 retBool 不能表达领域 int 计数，允许最小结果适配。
- 批量会话、提交和回滚直接遵循当前 BaseMapper 集合 API；不虚构自动加入调用方事务的承诺。成功写入后清除调用方 Mapper 会话的一级缓存。用真实 MySQL 验证批量落库、主键回填、写后读取和异常回滚。
- 删除父类已有六个方法的重复实现、四个链式别名及旧 PO 批量包装；Guice 注入直接面向唯一仓储类，保留 mapper 注入能力。
- 运行编译正负例、目标红绿测试、受影响模块与 Guice clean verify，以及无 record 门禁；既有工作区改动、分支和其他版本线保持。

## 跨分支同步（2026-11-25）

用户授权将当前 2.0.x 逻辑应用到维护分支，本分支为 3.0.x。此项扩展原仅限 2.0.x 的范围；沿用本文的行为验收与 BaseRepositoryImpl 入口约定。保留本分支 JDK、命名空间和依赖版本，验证上游组件、领域仓储、真实数据库和消费者。独立克隆实施，不使用 worktree，不覆盖既有工作区，不推送或远端发布。

## 注解及元数据驱动的主键修复（2026-11-25）

本项按用户新授权同时覆盖两个 MyBatis 模块与 1.0.x、2.0.x、3.0.x，覆盖前文仅限当前线的范围约束。

- 领域身份由 `DomainModel.id()` 提供；不猜测领域主键字段名。
- Plus 的主键字段/列名/生成策略直接复用 TableInfo；DomainField 的查询映射必须覆盖主键，PO 属性空间同样使用组件元数据。
- 原生 MyBatis 复用已解析的 ResultMap ID 标记及生成主键 keyProperty，只有没有显式主键映射时才以 id 属性兜底；映射冲突、缺失或非法类型必须报错，不能把错误当作空主键。
- 单条、列表、批量及写后回填均尊重既有 DomainObjectMapper；DomainField 继续负责查询字段映射，不另造对象转换器或注解。
- 真实 SQL 回归覆盖非 id 属性和列、继承主键、Model/PO 异名、普通 id 与显式主键共存、显式主键为空、新增/更新/条件删除及批量回填。
- 三线分别使用对应 JDK/依赖，保留 BaseRepositoryImpl，不修改业务库结构，不发布远端构件。
