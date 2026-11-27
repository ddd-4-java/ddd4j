# MyBatis 增强组件集成

规格：`../docs/superpowers/specs/2026-11-24-mybatis-enhance-integration.md`。

## 组件复用边界

| 能力                                        | 实际实现                                                            | DDD4J 职责                        |
|---------------------------------------------|---------------------------------------------------------------------|-----------------------------------|
| MyBatis 增强生命周期、加密、签名、解密      | mybatis-enhance-core / extension                                    | 装配                              |
| MyBatis-Plus 增强生命周期、加密、签名、解密 | mybatis-plus-enhance-core / extension                               | 装配、聚合填充                    |
| MP 租户、权限、分页、乐观锁、防全表更新     | MP 官方插件；增强组件提供租户处理器                                 | 提供租户与权限数据                |
| 原生租户 SQL                                | mybatis-enhance-extension TenantLineInnerInterceptor                | 提供 ThreadContext 租户值         |
| 原生注解权限                                | mybatis-enhance-extension DefaultDataPermissionStatementInterceptor | 应用配置已有组件的 handler/parser |
| SQL 观测                                    | 两套增强组件的 SqlObservation 插件与 SPI                            | 转换到原有请求上下文              |
| 租户作用域恢复                              | 两套增强组件 TenantContext                                          | 兼容旧 Scope 类型和上下文来源     |
| TypeHandler、i18n、INSERT IGNORE            | 上游现有实现                                                        | 按需注册，不复制实现              |

原生业务 Mapper 直接继承上游 `org.apache.ibatis.enhance.mapper.EnhanceMapper<P>`，原始密文查询方法需要对应 SQL
映射。装配器直接接收上游 EncryptedFieldHandler，允许直接使用组件默认处理器。旧 SqlObservation / Sink 保留历史 JSON
和调用契约，新增执行链消费上游 SPI。两种 ORM 模块存在相同历史包名，应用必须二选一；Enforcer 拒绝同时传递引入。

2.0.x 两个模块的装配入口统一为 `io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer`（Plus 原名
`Ddd4jMybatisPlusConfigurer`）。原生模块的三个 SQL 观测类型从 `io.ddd4j.data.mybatis.adapter` 迁至
`io.ddd4j.data.mybatis.plugins.observation`。外部应用需要更新 import 并重新编译；自定义 SPI 实现还需更新
`META-INF/services` 中的服务文件名和实现类名。

三条版本线都使用 `context`、`repository`、`repository.impl` 和 `plugins.observation` 放置对应职责的类型。SQL
观测的测试与生产类同包，租户和 Plus 拦截器测试也按被测对象归包。MyBatis-Plus 的 Configurer 在三条版本线均可使用。

## 装配

MyBatis-Plus 使用 `new Ddd4jMybatisConfigurer()`，按需调用 `tenant(column, ignoredTable)`、`dataScope(provider)`、
`encryption(handler)`、`signature(true)`，再将 `build()` 返回值注册到 Configuration。Guice 用 `enhancements(configurer)`
，Spring Boot 用同类型 Bean；默认仅装配基础保护、分页、聚合填充和观测。安全配置应在构造 SqlSessionFactory 前完成。

原生 MyBatis 使用 `Ddd4jMybatisConfigurer` 的同类租户、加密、签名配置。数据权限直接装配上游
`DefaultDataPermissionStatementInterceptor` 及其现有 parser/handler，不另造 DDD4J 权限语法。该上游权限插件只处理带权限注解的
SELECT，不能宣称覆盖写入授权。

MyBatis-Plus 三条发布线在分页前用官方权限插件约束 SELECT 和受支持的单表 UPDATE、DELETE。旧字符串条件可通过
`dataScope(statementId -> condition)` 接入，有类型的旧策略改为方法引用。结构化权限通过
`dataPermission(MultiDataPermissionHandler)` 装配，注解场景可使用上游 `DataScopeAnnotationHandler`
。同一装配器只能设置一种入口。解析失败时停止执行。自定义 COUNT 映射须验证与数据查询共享权限过滤。

原有本地权限和聚合回填拦截器已移除。直接注册原生 MyBatis 插件的调用方可使用上游 `QueryResultCallbackInterceptor`，将
`new AggregateQueryResultFiller()::fill` 作为回调。每个 SqlSessionFactory 只注册一条回填路径。旧类与有类型的权限入口涉及公共签名变更，升级需替换
import 和调用并重新编译。

## 原生查询与迁移

从旧版迁移时，业务 Mapper 将 `Ddd4jMapper<P>` 或 `Ddd4jQueryMapper<P>` 替换为上游 `EnhanceMapper<P>`。在业务 Mapper 上声明实际
SQL 对应的方法，保留既有 XML 的方法名和参数绑定。具体仓储实现以下四个受保护的方法，然后重新编译依赖这些旧类型或仓储基类的模块。

| 仓储方法                           | 业务 SQL 的职责                                                 |
|------------------------------------|-----------------------------------------------------------------|
| `selectRows(query, offset, limit)` | 绑定条件并稳定排序，数据库执行偏移与限量，负数 limit 表示不分页 |
| `countRows(query)`                 | 与选择记录共用过滤条件，统计全部匹配记录                        |
| `deleteRow(id)`、`deleteRows(ids)` | 按实际表的主键删除，返回影响行数                                |

无条件首条查询和总数查询传入 null。业务仓储负责将领域条件转换为业务 Mapper 的参数，对无法表达的条件抛出异常；示例见
`NativeQuerySqlTest`。SQL 值使用 `#{}` 绑定。Java 编译器检查具体仓储是否实现这四个方法，业务 XML 的 statement 和参数映射仍需通过真实
SqlSession 验证。

原生模块已移除未参与装配的 `io.ddd4j.data.mybatis.plugins.DataScopeProvider`。采用原生权限过滤的应用按上文装配增强组件的权限插件。

mybatis-enhance 2.0.x 的解析器基线从 3.1 变为 5.3。直接使用 SqlParserTool AST API 的客户端需要重新编译：SelectBody 改为
Select，SubSelect 改为 ParenthesedSelect；方法名保留。不能将新组件与 JSqlParser 3.1 同时装配。

原生租户插件覆盖 SELECT 物理表、单表 UPDATE/DELETE，以及显式携带且匹配当前租户的单行 VALUES INSERT。多表写入、批量 VALUES 与
INSERT SELECT 等无法安全确认的写法直接失败；共享表通过 ignoredTable 显式豁免。没有租户时失败。复杂业务 SQL
应先加入上游组件回归，再扩大支持范围。

## Guice 会话和事务

注入的 SqlSession 实际为 MyBatis 自带 SqlSessionManager，Mapper 可复用，底层会话按线程管理。单次 Mapper 操作自动创建和释放会话。多语句事务调用
startManagedSession (false)，成功 commit，异常 rollback，finally close；close 仅用于已开启的托管会话。不要跨线程传递托管事务或共享裸
DefaultSqlSession。

## 密码处理器

将对应增强库的 `DefaultEncryptedFieldHandler` 传给 `encryption(handler)`。MyBatis-Plus 使用
`com.baomidou.mybatisplus.enhance.crypto.handler` 包，原生 MyBatis 使用 `org.apache.ibatis.enhance.crypto.handler`
包。两个模块均已移除本地同名接口及接收 CryptoStrategy 的构造器。使用旧构造器的应用需按上游 API
配置密钥并重新编译。若已有密文和签名，切换写入前应使用历史样本验证解密和验签。

## 验证范围

H2 验证装配、租户、分页、count、删除、事务、两条 ORM 链的字段加解密与验签，以及 MP 的缓存重复读取；MP 仓储另有 Testcontainers
MySQL 8.4 回归。mybatis-enhance 组件使用 SQLite 验证加密、签名、缓存和篡改拒绝。正式验证结果记录于同日期交付报告，不能将一种数据库的结果外推到其他方言。
