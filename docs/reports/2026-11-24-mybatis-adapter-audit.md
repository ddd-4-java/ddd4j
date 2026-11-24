# MyBatis 双模块适配边界复核

审查范围为两个模块当前全部生产 Java 文件，并对照本地 mybatis-enhance / mybatis-plus-enhance 的 core、extension 实现。结论限定于通用增强执行机制：DDD4J 没有另行实现密码算法、密文格式、签名执行器、通用增强生命周期、租户 SQL 改写、分页 SQL、i18n 或 TypeHandler。公开旧类型的保留不等于维护第二套执行引擎。

| 保留类型 | 必要职责 | 已复用的上游能力 |
|---|---|---|
| 两个 Configurer | 组合 DDD4J 配置、上下文和组件 | 直接实例化组件加密、签名、解密、观测插件，以及 MP 官方租户、权限、分页、乐观锁插件 |
| 两个 Repository 与 BaseRepositoryImpl | AggregateRoot/PO 转换、DomainModelInfo 字段翻译、RepositoryRegistry、领域查询与回填 | 基础读写、批量执行、Wrapper 和分页委托 MyBatis/MP API；已删除原生内存过滤/排序/分页 |
| AggregateQueryResultFiller | 识别 Query、AggregateRoot 和 Page 并执行领域回填 | 查询回调和参数展开由上游 QueryResultCallbackInterceptor 负责 |
| 配置数据权限 | 在 MyBatis-Plus 链中装配应用的权限策略 | 使用官方 DataPermissionInterceptor 和上游权限处理器 |
| Ddd4jTenantContext 与 Scope | 用 ThreadContext 作为租户唯一来源，保留旧 Scope 返回类型 | 作用域进入、恢复和重复关闭逻辑委托上游 TenantContext.Scope；不建立另一份租户线程变量 |
| SqlObservation / SqlObservationSink | 保持旧字段名、JSON、构造器和消费接口契约 | 新执行链消费上游 SPI；旧 DTO 只承担数据与兼容性职责 |
| Ddd4jSqlObservationSink | 把上游观测事件映射到 DDD4J 请求上下文字段 | 不计时、不执行 SQL、不自己发布执行事件 |

原生 Mapper 直接继承上游 EnhanceMapper，具体仓储显式绑定查询、计数、单删和批删 SQL。原生模块已移除未参与装配的 DataScopeProvider。

## 本次进一步收敛

移除了三条版本线的 MyBatis-Plus 本地权限和聚合回填拦截器。增强组件负责查询回调和 SQL 条件适配，DDD4J 保留领域对象回填。

没有引入新的 XxxUtil、SQL 解析器、密码格式、国际化处理器、TypeHandler 或增强执行框架。原生租户 SQL 能力位于 mybatis-enhance-extension，两个 DDD4J 模块不维护它的副本。

## 兼容性边界

两种模块仍有相同历史包名，适合按应用二选一；POM 中的 Enforcer 拒绝同时传递引入。两个模块已移除本地字段加密接口及默认处理器，迁移说明见 ddd4j-data/README-mybatis-integration.md。上游原生注解权限插件只处理 SELECT，不能据此宣称支持所有写入授权语义。
