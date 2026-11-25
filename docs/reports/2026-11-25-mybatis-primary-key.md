# MyBatis 主键与对象映射三线修复

日期：2026-11-25。范围：原生 MyBatis、MyBatis-Plus；三条本地 feature/1.0.x、feature/2.0.x、feature/3.0.x。规格沿用 `../superpowers/specs/2026-11-24-mybatis-enhance-integration.md` 的“注解及元数据驱动的主键修复”。

## 修改原因与影响

| 位置 | 原问题 | 修复及影响 |
| --- | --- | --- |
| 原生仓储 extractId / setIdValue | 扫描名为 id 的字段；忽略实际主键，导致重复插入、条件更新遗漏、删除错误记录 | 领域对象使用 DomainModel.id()；PO 使用 ResultMap 显式 ID 属性，其次 insert keyProperty，最后才兼容 id。通过 MyBatis MetaObject 访问继承属性，多显式主键在写入前报错 |
| Plus configureTypes | TableInfo.fieldList 不包含主键，DomainField 主键查询被翻译为错误列名 | 补用已有 TableInfo.keyProperty / keyColumn；普通字段维持现有组件元数据 |
| Plus convert | 列表直接 BeanKit.copy，绕过 DomainObjectMapper | 列表逐个调用现有 toModel，支持 businessId → orderKey → order_pk |
| Plus save / updateById / completeBatch / 条件更新回填 | PO 直接复制到 Model，异名主键未回填 | 先调用既有 toModel，再回填原聚合对象；保留原对象返回契约 |

未增加依赖、ID 注解、元数据缓存或通用工具类；BaseRepositoryImpl 保留。原生 MyBatis 没有公开的 Mapper 主键属性查询 API，最小适配只读取已有 MappedStatement / ResultMap；继承命名空间交给 MapperMethod.SqlCommand，属性访问交给 MetaObject，不重写注解解析器。标准 MapperProxy 的 SqlSession/mapperInterface 通过 MetaObject 读取；额外装饰的 Mapper 可覆盖 persistenceIdProperty()。复合主键仍不属于单 ID 仓储契约。

DomainField 继续用于查询字段映射；异名对象转换由已有 DomainObjectMapper 的 toModel / toPersistenceObject 承担。未将 DomainField 扩展成另一套 Bean 映射实现。

## 行为验证

- 红灯：生产代码修改前，新增原生四项 SQL 测试全部失败（重复主键、错误更新目标、生成主键未返回、错误删除目标）；Plus 新增五项真实 MySQL 测试全部失败（错误列名、列表主键丢失、单条/批量回填失败）。原有 MySQL 十项仍通过。
- 原生新增七项 H2 + 真实 SqlSession 测试：非 id 名称、父类主键、异名 Model/PO、普通 id 干扰、空显式主键、条件更新/删除、keyProperty 回退、无元数据时 id 兼容、多 ID 映射拒绝。
- Plus 新增五项 MySQL 8.4 测试：继承 @TableId 与 DomainField、生成主键回填、领域/PO 查询、分页/投影、列表转换、批量插入/更新/upsert、条件更新/删除。原有回归保留。

三线均执行各自 Maven Wrapper：

```sh
bash mvnw -o -pl :ddd4j-data-mybatis,:ddd4j-data-mybatisplus,:ddd4j-runtime-guice -am clean verify
```

| 分支 | JDK | Reactor 项目 | 全部测试 | 原生模块 | Plus 模块 | Guice |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 1.0.x | Corretto 8.0.504 | 24 | 1308 | 17 | 72 | 101 |
| 2.0.x | Corretto 17.0.21 | 14 | 975 | 17 | 72 | 127 |
| 3.0.x | Microsoft 21.0.13 | 24 | 1308 | 17 | 72 | 101 |

全部 0 失败、0 错误、0 跳过。两模块三线均为 17 + 72 项；总体数量差异来自各分支原有 Guice 依赖及测试集合。Javadoc、源码与 JAR 打包随 verify 完成；未启用可选覆盖率门槛，未将 JaCoCo 报告生成解释为覆盖率达标。

三线 git diff --check、无 record 扫描通过；扫描器自测分别 5 / 7 / 5 项通过。主键实现及新测试三线一致，保留各自 Java 语法、Testcontainers 与依赖版本。未进行远端 CI、部署或生产验证。

## 交付与工作区

使用原有独立克隆验证 1.0.x / 3.0.x；没有创建 worktree，没有切换当前 2.0.x 工作区。只纳入本次七个实现/测试/说明文件及本报告；原有暂存区内容通过 SHA-256 核对保留。三线落为本地提交，不推送、不发布远端构件，不操作业务数据库。

完整本地日志和分支校验记录：`../../../deliveries/mybatis-primary-key-20261125/`（红灯日志、三线 verify 日志、verification.json、static-checks.json、manifest.json）。
