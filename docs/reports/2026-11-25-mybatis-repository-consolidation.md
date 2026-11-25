# MyBatis-Plus 聚合仓储收敛验证

日期：2026-11-25。仓库分支：`feature/2.0.x`。规格事实源：`../superpowers/specs/2026-11-24-mybatis-enhance-integration.md` 的“聚合仓储收敛”章节。

## 实现结果

- 保留 `repository.impl.BaseRepositoryImpl` 作为业务继承入口，五个泛型参数保持。移除三处重复类型解析，全部继承 `MybatisAggregateRepository` 的实现。
- 两个入口均约束 `M extends AggregateRoot<ID>`，聚合 ID 与仓储 ID 不一致时编译失败。
- `MybatisAggregateRepository` 组合 `BaseMapper`，解除领域 M 与 PO P 同时继承 Repository 带来的擦除冲突。移除重复 PO CRUD、批量包装、链式别名和绕过泛型的旧入口。
- 实现 core 的三个领域批量方法，直接调用 Mapper 集合 API。DDD4J 仅保留模型转换、业务字段填充、原聚合回写和领域计数适配。
- 使用 `TableInfo.getPropertyValue` 读取主键；私有日期字段通过已有 Commons `FieldUtils` 读写，支持继承主键及私有填充字段。
- 组件批量会话写入后，通过 `MybatisUtils.getMapperProxy(...).getSqlSession().clearCache()` 清除原会话一级缓存。
- Guice 面向公共父类直接注入 Mapper，删除反射方法调用；保留 `BaseRepositoryImpl` 的具体业务仓储可正常注入。

## 验证证据

目标红灯真实触发：错误 ID 组合原先可以编译；三个领域批量方法原先抛出 `UnsupportedOperationException`。目标测试首次得到 3 项失败、4 项错误，匹配 ID 的正例通过。缓存回归另行复现：批量更新后，同一仓储期望 `updated`，实际仍读到 `first`。

最终使用 Corretto 17.0.21 执行：

```sh
./mvnw -o -pl :ddd4j-data-mybatisplus,:ddd4j-runtime-guice -am clean verify
python3 scripts/check_no_records.py
python3 -m unittest discover -s scripts -p 'test_check_no_records.py'
```

13 个 Maven 项目全部成功，Surefire XML 合计 953 项测试、0 失败、0 错误、0 跳过。

| 模块 | 测试数 |
| --- | ---: |
| annotation | 26 |
| kit | 76 |
| core | 591 |
| data-crypto | 10 |
| cache | 52 |
| runtime-testkit | 4 |
| data-mybatisplus | 67 |
| runtime-guice | 127 |

核心新增/补充行为：

- 两个仓储入口的 ID 编译正负例，共 4 项。
- 领域批量 6 项：主键与字段回写、实际计数/0/SUCCESS_NO_INFO、混合 upsert、空集合、非法元素、JDBC 失败标记。
- 真实 MySQL 8.4（Testcontainers）现共 10 项，包括 Long 自增继承主键、批量插入/更新/upsert、同一仓储写后读取，以及唯一键冲突后的整批回滚。测试表仅存在于临时容器，不改开发库或正式库。
- `BaseRepositoryImpl` 泛型解析回归通过；Guice 实际创建继承该入口的仓储，注入 Mapper 后成功执行查询。
- 无 record 扫描及其 7 项自测通过；范围内 `git diff --check` 通过。构建 JAR 同时包含 `BaseRepositoryImpl` 和 `MybatisAggregateRepository`；Javadoc 与 JaCoCo report 成功。

构建初次遇到本机缓存中增强组件缺少当前源码已有类型，已在本地 `mybatis-plus-enhance` 仓库执行 `mvn -o install` 补齐匹配快照，166 项测试通过。此操作未修改上游源码、未向远端发布。最终 DDD4J 构建走正常 Maven 依赖解析。

本机执行日志：`/tmp/mybatis-repository-red.log`、`/tmp/mybatis-repository-cache-red.log`、`/tmp/mybatis-repository-final-verify.log`、`/tmp/mybatis-repository-component-install.log`。

## 兼容性和验证边界

`BaseRepositoryImpl` 保留，公共逻辑只有一份；旧 PO 包装、同义方法及 `AbstractRepository` 继承关系调整会影响消费方源码/二进制兼容性，需按数据模块 README 迁移并重新编译。错误 ID 组合被拒绝是本次预期行为。

批量事务遵循组件 API，未声明或验证自动加入调用方外层事务。测试验证的是单次组件批量成功提交及失败回滚，以及自动提交查询会话下的缓存一致性，不扩展为任意事务隔离级别的可见性保证。

本次没有验证其他 Java 版本线、远端 CI 或生产环境，也未启用可选 90% 覆盖率 profile；默认 verify 成功不代表该覆盖率门槛通过。未提交、推送或远端发布，既有工作区改动保留。
