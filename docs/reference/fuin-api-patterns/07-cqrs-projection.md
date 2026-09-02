# 07. fuin API 模式：CQRS 投影读侧

> 对应 README 索引第 07 项；本篇为「已对齐」主题——ddd4j-core 现有 `cqrs/readmodel/` 投影抽象（16 类）已与 fuin 对齐，并在框架无关、运行器复用、仓储分离、类型分发四处超出；重在盘点存量与微调，不新增 API 借鉴。

## 来源

- 仓库：https://github.com/fuinorg/cqrs-4-java
- 版本：0.6.0（本地快照：`workspace-ddd4j-boot/cqrs-4-java`，tag `0.6.0`）
- 文件：
  - `core/src/main/java/org/fuin/cqrs4j/core/View.java:10-27`（视图契约：name＋eventTypes）、`JpaView.java:12-40`（叠加 cron／chunkSize 默认 100／`handleEvents(EntityManager, List<Event>)`）
  - `esc/src/main/java/org/fuin/cqrs4j/esc/ProjectionService.java:26-58`（投影位置三方法契约）
  - `springboot/src/main/java/org/fuin/cqrs4j/springboot/view/`：`QryProjectionPosition.java:15-80`（JPA 位置实体）、`QryProjectionService.java:15-56`（直接持 EntityManager 的实现）、`SpringJpaViewManager.java`（264 行：视图生命周期＋分块循环全部内联）
  - 平行实现：`quarkus/src/main/java/org/fuin/cqrs4j/quarkus/view/QuarkusJpaViewManager.java`（190 行，用 `io.quarkus.scheduler.Scheduler`，:7/:44/:71）
- 关键 API：`resetProjectionPosition`／`readProjectionPosition`（不存在返回 0）／`updateProjectionPosition`（esc ProjectionService.java:35/:46/:56）
- 注意（Task 1.7 已核实）：fuin 的 ViewManager 只有 Spring／Quarkus 两份框架专用实现，核心层无投影循环抽象；springboot 模块整体即投影侧支撑（view/＋base/EventstoreConfig.java，无命令侧设施）

## fuin 的设计

读侧三件套：View 声明「我关注什么」→ ProjectionService 记「我读到哪」→ ViewManager 定时「拉一段、投一段、推位置」。

**1）视图契约——JpaView（JpaView.java:12-38）**

```java
public interface JpaView extends View {
    String getCron();                                  // Spring Quartz CRON
    default int getChunkSize() { return 100; }         // 单事务事件数
    void handleEvents(EntityManager em, List<Event> events);
}
```

**2）投影位置三方法——ProjectionService（esc ProjectionService.java:35/:46/:56）**：reset（回起点）／read（上次位置，缺省 0）／update（写新位置）；Spring 实现即 em.find＋persist（QryProjectionService.java:26/:36/:47-53），位置存于可变 JPA 实体（QryProjectionPosition.java:15-26）。

**3）视图管理器——SpringJpaViewManager（264 行）**：`SchedulingConfigurer.configureTasks` 为每个视图注册 CronTask（:101-104/:117-118），`ContextClosedEvent` 时逐任务 cancel（:106-109/:123-133）；cron 触发 `updateView`→tryLocked＋`new Thread`（:136-146）→ `readStreamEvents`：投影流不存在则 createProjection（:151-158）→ readProjectionPosition（:162）→ `readAllEventsForward` 分块回调 `handleChunk`（:163-164）→ REQUIRES_NEW 事务内 handleEvents＋updateProjectionPosition（:172-181）。投影流 ID＝视图名＋事件类型集合 Adler32 校验和（:203-205）。

## 优点（值得借鉴的）

- 一个接口声明一个投影：name＋eventTypes＋cron＋chunkSize 四元即成（JpaView.java:19-38），javadoc 自述省掉 Projector／EventDispatcher／ChunkHandler 三类样板（SpringJpaViewManager.java:44-46）。
- 位置缺省 0 的自启动语义（QryProjectionService.java:37-39）：新视图免初始化步骤即可增量拉取。
- 投影流 ID 掺入事件类型集合校验和（SpringJpaViewManager.java:203-205）：视图关注的事件类型变化后自动切新流，旧流数据不污染新定义。
- createProjection 容忍竞态（SpringJpaViewManager.java:156-158）：并发创建撞 StreamAlreadyExistsException 仅记日志不失败。
- Semaphore(1)＋tryLocked 防同视图重入（SpringJpaViewManager.java:137/:207）：调度重叠时丢弃本次而非任务堆积。

## 缺点（应规避的）

- **调度生命周期写死 Spring**：@Component＋@Order(0)＋implements SchedulingConfigurer／`ApplicationListener<ContextClosedEvent>`（SpringJpaViewManager.java:48-50），任务注册（:117-118）与取消（:125-131）全走 Spring 类型；Quarkus 需整份重写（QuarkusJpaViewManager.java，190 行）——同一投影循环两份维护。
- **投影循环无独立抽象**：「读位置→拉块→投影→推位置」内联在 264 行管理器里（SpringJpaViewManager.java:148-181），且直连 esc EventStore API（:163-164），换调度器或存储无法复用。
- cron 每跳 `new Thread`（SpringJpaViewManager.java:137-145）：裸线程、无线程池，RuntimeException 仅 LOG.error 吞掉（:141-143）。
- 事务细节泄漏进投影循环：TransactionTemplate REQUIRES_NEW＋硬编码 10s 超时（SpringJpaViewManager.java:96-98）＋getTransactionalEntityManager（:176）——非 JPA 读模型（Redis/ES）无法套用。
- 字符串类型往返：asTypeNames 把 `Set<EventType>` 映射为 TypeName 字符串（SpringJpaViewManager.java:168-170），asEvents 裸强转 `(Event) event.getData()`（:184），类型分发全靠业务侧 instanceof。
- 服务与仓储合体＋可变实体：QryProjectionService 直接持 @PersistenceContext EntityManager（QryProjectionService.java:20-21），QryProjectionPosition 暴露公开 setter（QryProjectionPosition.java:77-80），无不可变保护。

## ddd4j 自研决策

> **结论：ddd4j-core `cqrs/readmodel/`（16 类＋package-info）已对齐 fuin 投影契约，并在框架无关、运行器复用、仓储分离、类型分发四处超出；API 层零新增借鉴。**

- **借鉴（新增）**：无——三方法、视图四元配置、生命周期语义均已覆盖；唯一实现层微调建议（投影流 ID 派生）见落地计划第 3 条。
- **已对齐（对等）**：
  - 位置三方法：ddd4j ProjectionService（ProjectionService.java:19/:27/:36）↔ fuin esc ProjectionService.java:35/:46/:56 逐方法对齐；「缺省 0」语义同（DefaultProjectionService.java:26-28 ↔ QryProjectionService.java:37-39）。
  - cron＋chunkSize 视图配置：ProjectionView.java:29/:34-36（chunkSize 默认同为 100）↔ JpaView.java:19/:27-29；name＋eventTypes 同（ProjectionView.java:17/:41 ↔ core View.java:17/:24）。
  - 生命周期语义：ViewManager 的 start/stop/triggerOnce（ViewManager.java:30/:35/:47）↔ fuin createViews/shutdownViews/updateView（SpringJpaViewManager.java:111/:123/:136）。
  - 「先投影后推位置」顺序：ProjectionRunner.runOnce（ProjectionRunner.java:46-51）↔ handleChunk（SpringJpaViewManager.java:177-178）；fuin 以 REQUIRES_NEW 包裹两步（:173），ddd4j 把事务边界留给适配层（见改写 (a)）。
- **改写／超出（逐条核实）**：
  - (a) 纯 Java 框架无关：ddd4j 整个 readmodel 包零框架依赖，ViewManager（ViewManager.java:25-48）与 ViewScheduler（ViewScheduler.java:18-44，含 ViewScheduleHandle）均为 SPI，调度策略留给各运行时；fuin 的 ViewManager 就是 Spring 类本身（SpringJpaViewManager.java:48-50），Quarkus 另抄一份（QuarkusJpaViewManager.java）。
  - (b) 循环沉淀为可复用纯类：`ProjectionRunner.runOnce`「读位置→EventChunkReader.read→handleEvents→updatePosition」（ProjectionRunner.java:36-53），事件存储经 EventChunkReader SPI 解耦（EventChunkReader.java:26，Noop 兜底 NoopEventChunkReader.java:15-21）；fuin 该循环内联于 264 行 SpringJpaViewManager（:148-181）且直连 EventStore（:163-164）。
  - (c) 服务／实体／仓储三分离：接口＋DefaultProjectionService 委托仓储（DefaultProjectionService.java:20-36）＋ProjectionPositionRepository SPI（ProjectionPositionRepository.java:29-56，InMemory 实现零依赖可跑 :18-59）；位置是不可变值对象（DefaultProjectionPosition.java:22/:46-51，withNextEventNumber）且 update 返回持久化实例（ProjectionService.java:36）；fuin 服务直持 EntityManager（QryProjectionService.java:20-21）、位置为可变 JPA 实体（QryProjectionPosition.java:77-80）、update 返回 void（esc ProjectionService.java:56）。
  - (d) 类型分发：TypedEventDispatcher／TypedEventHandler 以 `getEventClass()` 做 isInstance 校验后 cast（TypedEventDispatcher.java:77-80、TypedEventHandler.java:20/:27），替代业务侧 instanceof 墙；fuin 视图只收 `List<Event>`（JpaView.java:38）。
- **不借鉴**：
  - Spring 专用调度器实现（SchedulingConfigurer／CronTask／ContextClosedEvent／裸线程）——阶段 7 按运行时各写 ViewScheduler 实现；
  - TransactionTemplate＋EntityManagerFactory 硬接线（SpringJpaViewManager.java:173/:176）——事务边界归框架适配层；
  - 字符串 eventType 路由（SpringJpaViewManager.java:168-170）——处理器层用 Class 校验分发（TypedEventHandler.java:20）。

## 落地计划

- [ ] 阶段 7（Task 7.1）：ddd4j-data-projection 直接复用 ddd4j-core readmodel 既有 16 类（ProjectionRunner／ProjectionView／ProjectionService／EventChunkReader 等），不新起契约；4 套持久化（jpa/panache/jdbi/r2dbc）各自实现 ProjectionPositionRepository。
- [ ] 阶段 7（Task 7.7-7.13）：7 个运行时 ViewScheduler 按 ViewScheduler.java:18-44 的 SPI 实现（Spring TaskScheduler／Quarkus @Scheduled／其余 ScheduledExecutorService），ViewManager 装配 start/stop/triggerOnce；并发重入防护一并下沉到调度器实现。
- [ ] 阶段 7（微调）：投影流 ID 派生纳入事件类型集合指纹（借鉴 SpringJpaViewManager.java:203-205 的 Adler32 思路），防视图事件集变更污染旧流。
- [ ] Task 1.10：ADR-0003（跨 8 运行时约束）引用本文档「投影抽象框架无关＋ViewScheduler SPI」结论。
