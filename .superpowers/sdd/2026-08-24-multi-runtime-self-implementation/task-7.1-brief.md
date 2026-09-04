# Task 7.1 Brief — ddd4j-data-projection 模块骨架（ProjectionHandler SPI）

## 背景（已核事实）
- ddd4j-core/cqrs/readmodel/ **已含 16 个契约**（不再重定义）：ProjectionService/ProjectionPosition/ProjectionView/ProjectionPositionRepository/EventChunk/EventChunkReader/ViewManager/ViewScheduler/ProjectionRunner/TypedEvent/TypedEventHandler/TypedEventDispatcher+DefaultProjectionService+DefaultProjectionPosition+NoopEventChunkReader+InMemoryProjectionPositionRepository+package-info。
- **本模块不重定义任何 projection 抽象**——只补**业务侧可编程的 `ProjectionHandler` 入口 SPI**（阶段 7.6+ 七调度器按此合同"装配"projection 业务 bean），加模块 ArchUnit 与 pom 骨架。

## 交付（io.ddd4j.data.projection 包）

### A. `ProjectionHandler` 接口（同包 SPI 层）
```java
public interface ProjectionHandler {
    String getName();                              // 视图唯一标识
    Collection<Class<? extends DomainEvent<?>>> eventTypes();  // 本 handler 订阅的事件类型
    void handle(DomainEvent<?> event);             // 单事件增量应用
    default String getCron() { return "0/5 * * * * *"; }  // 调度 CRON（业务可 override）
    default int getChunkSize() { return 100; }             // 拉块大小
}
```
- 类型签名全部用 ddd4j-core 现有契约（DomainEvent 是 core 抽象）——**不重复任何 interface**。
- 业务实现类示例在阶段 8 sample（仅文档），本任务**不写 sample 业务实现**。

### B. `ProjectionHandlerRegistry`（注册中心）
- `void register(ProjectionHandler h)` 校验：handler 事件类型去重（已注册同类型→抛 IllegalStateException 含类型 FQCN，javadoc 注明与 core/CommandRegistry 同源语义）。
- `Collection<ProjectionHandler> all()`（不可变视图）。
- `<E extends DomainEvent<?>> Optional<ProjectionHandler> findHandler(Class<E> eventType)`（未注册返回 Optional.empty()——dispatcher 决定兜底策略）。

### C. `ProjectionDispatcher`（core TypedEventDispatcher 之上的门面）
构造器 `(ProjectionHandlerRegistry registry, EventChunkReader<DomainEvent<?>> chunkReader, ProjectionPositionRepository positions, ProjectionService service)`；`Flux<DomainEvent<?>> chunkByEvent(Class<E>)` 返回按 handler 订阅类型过滤的流；`CompletableFuture<Void> dispatchOne(DomainEvent<?>, ProjectionHandler)` 顺序应用并 position++.commit。**不**包事务（与 ddd4j-data-cqrs-spring 同款——业务 bean 自管事务）。

### D. 模块 pom
- parent ddd4j-data
- 依赖 `ddd4j-core` + `ddd4j-annotation`（均 ${revision}）
- test 依赖走父级全局块（junit/assertj/archunit/...——不加 Spring/Quarkus/Micronaut/Helidon/Javalin/Vertx/Dropwizard，**架构无关**）
- 5 个 ArchUnit 规则：`projection_impl_deps_allowlist`（io.ddd4j.data.projection.. onlyDependOn io.ddd4j../java../lombok..）+ no_spring + no_quarkus + no_micronaut + no_vertx（**no_vertx** 防止误引 vertx 容器依赖；与 stage 6 一致约定）
- 测试类 ≥2：`ProjectionHandlerRegistryTest`（去重、Optional.empty 行为）+ `ProjectionDispatcherTest`（单事件 dispatch 行为，注入 mock chunkReader/repo/handler）

### E. ddd4j-data/pom.xml 注册
**字母序**：event-store 后、event-store-jdbi 前？实际按 ddd4j-data 全列表字母序排——pro 段首模块**应在 ddd4j-data-event-store-r2dbc 之后**（r 段），前面 cqrs/crypt 间隔。需实测定位精确槽位。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` BUILD SUCCESS；core 261 不变；本模块 ≥5 测试（2 类 + 3 ArchUnit；brief 要求 ≥5 总数）。

## 提交
单 commit：`feat(data): ddd4j-data-projection——ProjectionHandler SPI + Registry + Dispatcher（不重定义 core 契约）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.1-report.md`。Reply ≤15 lines.
