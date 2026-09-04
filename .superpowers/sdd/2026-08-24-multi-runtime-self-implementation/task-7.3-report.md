# Task 7.3 Report — ddd4j-data-projection-panache（Quarkus Panache 投影持久化）

## Status: DONE

## Commit
- `5059bdb1` feat(data): ddd4j-data-projection-panache——Quarkus Panache 投影持久化（含 H2 IT）（6 files, +341）
- **共享仓竞态注记**：模块 pom（91 行）与 `PanacheProjectionPositionEntity.java`（81 行）被并行任务
  `27c30547`（"删除误建的 ddd4j-ai-dependencies 模块"）的宽 `git add` 提前裹挟提交——内容与本任务
  产出逐字节一致（工作树无残留改动即为证），未重写他人提交（防并行链断裂）。最终树状态与单 commit
  预期完全一致，仅历史归属被拆成两笔。

## Gate
- `./mvnw -pl ddd4j-data/ddd4j-data-projection-panache -am install` → **BUILD SUCCESS**
- 模块精确计数（surefire）：
  - `QuarkusProjectionPositionRepositoryIT`（@QuarkusTest H2）：**4/4** 通过
    （save→advance→reload 跨持久化上下文循环；resetToZero 推进后归零；resetToZero 缺行插入零位；upsert/delete/findAll 双流命名空间隔离）
  - `ProjectionPanacheModuleIndependenceTest`（ArchUnit）：**3/3** 通过
    （projection_panache_deps_allowlist / no_spring_in_panache_module / no_vertx_in_panache_module）
  - 合计 7/7；主代码 2 类（实体 + 适配器），测试 3 类 + application.properties
- **dependency:tree 实证（MANDATORY）**：
  - `io.quarkus:quarkus-hibernate-orm-panache:jar:3.38.2:compile`
  - `io.quarkus:quarkus-hibernate-orm:jar:3.38.2` → `org.hibernate.orm:hibernate-core:jar:7.4.5.Final`
  - 即模块级 quarkus-bom 3.38.2 再导入生效：hibernate-core 对齐 7.4.5.Final（Quarkus 线），
    未被父 BOM 链的 Framework 7 线管理项（7.2.6）压版——5.1 教训防御验证通过。

## 交付明细
- `ddd4j-data/ddd4j-data-projection-panache/pom.xml`：parent ddd4j-data；quarkus-bom `${quarkus.version}`
  模块级再导入（parent-chain 防御，同 5.1 event-store-panache 模式）；依赖
  `ddd4j-data-projection`（${revision}）+ `quarkus-hibernate-orm-panache`（BOM 无版本）；
  test：`quarkus-junit5` + `quarkus-jdbc-h2`（BOM 无版本）；surefire 追加 `**/*IT.java`
- `PanacheProjectionPositionEntity`：extends `PanacheEntityBase`；`@Entity @Table(name="ddd4j_projection_position")`
  与 jpa 模块同表同列（stream_id 自然主键＝唯一键，length 250；next_event_number）；公有字段 Panache 约定；
  non-versionable（同 jpa 模块设计）；静态 `upsert`（find→null 则 new+persist，否则更新+persist）与
  `resetToZero`（upsert(streamId, 0)——缺行插入零位语义）
- `QuarkusProjectionPositionRepository`：`@ApplicationScoped` implements core SPI（5 方法全实现）；
  写路径 `jakarta.transaction.Transactional`；读路径转不可变 `DefaultProjectionPosition` 返回
- 注册：`ddd4j-data/pom.xml` 于 projection-jpa 之后（字母序 "j" < "p"）

## 对 brief 的有意偏离（自查披露）
1. **未按 brief 字面「构造器 `@Inject PanacheProjectionPositionEntity repository`」**：active record
   实体非 CDI Bean，ArC 无可注入类型（@QuarkusTest 将启动即 UnsatisfiedDependency）——按 5.1 已验证
   先例（`PanacheEventStore` 静态委托实体原语、无实体注入）实现，javadoc 已说明理由。
2. **IT advance 循环首版（读当前位→save 推进位）实测失败**（expected 5 but was 1）：@QuarkusTest 长生命周期
   Session 一级缓存使事务外读回陈旧 0；改为 dispatcher 实际用法（内存推进 + save 落库），
   终态 `entityManager.clear()` 后跨持久化上下文读回 5。
3. application.properties 在 brief 的 `jdbc:h2:mem:projit;MODE=PostgreSQL` 上补 `DB_CLOSE_DELAY=-1`
   （5.1 实证必需项），devservices=false + 显式 URL + drop-and-create 均落实。

## Concerns
- 上述共享仓竞态（两文件历史归属在 27c30547）；如需干净单 commit 历史，建议人工 squash 27c30547+5059bdb1。
- resetToZero 走 upsert（同事务 find+update）而非 jpa 模块的数据库端原子批量 UPDATE——H2 IT 全过；
   高并发追赶场景若需原子自增，jpa 模块的 `incrementBy` 扩展未在本模块复制（brief 未要求）。
