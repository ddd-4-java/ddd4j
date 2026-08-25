package io.ddd4j.data.projection;

import io.ddd4j.core.ddd.event.DomainEvent;

import java.util.Collection;

/**
 * 投影处理器 SPI（业务方实现的读侧入口）。
 *
 * <p>本接口是阶段 7 投影侧唯一新增的业务编程面：投影的全部基础设施契约
 * （{@code ProjectionService}／{@code EventChunkReader}／{@code ProjectionPositionRepository}／
 * {@code ViewScheduler} 等 16 个抽象）已由 ddd4j-core 的
 * {@code io.ddd4j.core.cqrs.readmodel} 包定义，本模块<b>不重定义任何投影抽象</b>——
 * 只把「业务想订哪些事件、怎么更新读模型」收敛成一个可被各运行时调度器
 * （阶段 7 Task 7.7+：{@code ddd4j-data-projection-spring} / {@code -quarkus} / ...）
 * 统一装配的合同。
 *
 * <p>运行时调度器按 {@link #getCron()} 周期触发，经
 * {@link ProjectionHandlerRegistry} 找到订阅了对应事件类型的 handler，
 * 再由 {@link ProjectionDispatcher} 拉块、逐事件回调 {@link #handle(DomainEvent)}
 * 并推进投影位置。
 *
 * <h3>与 core ProjectionView 的关系</h3>
 * <p>core 的 {@code ProjectionView} 面向「框架适配层装配的视图对象」（事件类型用字符串声明）；
 * 本接口面向「业务 bean」（事件类型用 {@link Class} 声明，编译期即可校验）。
 * 调度器适配层负责把本接口适配为 core 的 {@code ProjectionView}／
 * {@code ProjectionRunner} 所需形态，业务代码不需要感知两者差异。
 *
 * <h3>使用示例（业务实现，阶段 8 sample 将给出完整版）</h3>
 * <pre>{@code
 * public class OrderSummaryHandler implements ProjectionHandler {
 *     &#64;Override
 *     public String getName() { return "order-summary"; }
 *
 *     &#64;Override
 *     public Collection<Class<? extends DomainEvent<?>>> eventTypes() {
 *         return Set.of(OrderCreated.class, OrderPaid.class);
 *     }
 *
 *     &#64;Override
 *     public void handle(DomainEvent<?> event) {
 *         // 单事件增量更新读模型（事务由业务 bean 自管，本 SPI 不包装）
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionHandlerRegistry
 * @see ProjectionDispatcher
 * @since 2.0.x
 */
public interface ProjectionHandler {

    /**
     * 视图唯一标识（同时作为投影流 ID，与 core {@code ProjectionView}
     * 的「streamId 默认取视图名」约定一致）。
     *
     * @return 视图名称，非空白
     */
    String getName();

    /**
     * 本 handler 订阅的事件类型集合。
     *
     * <p>注册到 {@link ProjectionHandlerRegistry} 时按类型去重——同一事件类型
     * 只允许一个 handler 订阅（重复注册在装配期整批拒绝）。
     *
     * @return 订阅的事件类型，非空且不含重复
     */
    Collection<Class<? extends DomainEvent<?>>> eventTypes();

    /**
     * 单事件增量应用（由 {@link ProjectionDispatcher} 顺序回调）。
     *
     * <p>本方法抛出异常时该事件的投影位置不推进（由 dispatcher 保证先应用、
     * 后提交位置），失败重试策略由调度器与业务侧决定；事务由业务 bean 自管，
     * SPI 层不包装事务（与 ddd4j-data-cqrs-spring 的命令侧同款约定）。
     *
     * @param event 订阅类型之一的领域事件，非空
     */
    void handle(DomainEvent<?> event);

    /**
     * 调度 CRON 表达式（6 段式，含秒；语义同 core {@code ViewScheduler} 传入值）。
     *
     * @return CRON 表达式，默认 {@code "0/5 * * * * *"}（每 5 秒）
     */
    default String getCron() {
        return "0/5 * * * * *";
    }

    /**
     * 单次拉块大小（透传给 core {@code EventChunkReader#read} 的 chunkSize）。
     *
     * @return 拉块大小，默认 100，须为正数
     */
    default int getChunkSize() {
        return 100;
    }
}
