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
