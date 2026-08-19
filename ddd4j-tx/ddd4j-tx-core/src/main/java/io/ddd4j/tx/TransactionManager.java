package io.ddd4j.tx;

/**
 * 分布式事务管理器（统一入口）。
 *
 * <p>屏蔽 TCC / AT / Saga 三种模式的底层差异，对外提供统一的
 * begin / commit / rollback / getStatus 语义；业务层只需依赖此接口，
 * 具体实现由运行时适配器（如 {@code ddd4j-runtime-spring} 的 Seata 适配）注入。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * TransactionManager tm = Contexts.getOrThrow(SpiKeys.TRANSACTION_MANAGER, TransactionManager.class);
 * TransactionContext ctx = tm.begin("order-payment", 60_000);
 * try {
 *     // 业务逻辑
 *     order.pay(amount);
 *     inventory.deduct(sku, quantity);
 *     tm.commit(ctx);
 * } catch (Exception e) {
 *     tm.rollback(ctx);
 *     throw e;
 * }
 * }</pre>
 *
 * <h3>与 TransactionPort 的关系</h3>
 * <ul>
 *   <li>{@link TransactionPort}：轻量级事务边界（{@code void execute(Runnable)}），适合单服务本地事务</li>
 *   <li>{@link TransactionManager}：完整事务生命周期管理（begin/commit/rollback），适合跨服务分布式事务</li>
 *   <li>两者可共存：本地事务用 TransactionPort，跨服务用 TransactionManager</li>
 * </ul>
 *
 * <h3>SPI 注册</h3>
 * <pre>{@code
 * // 框架适配层启动期注册
 * Contexts.register(SpiKeys.TRANSACTION_MANAGER, TransactionManager.class, seataManager);
 * }</pre>
 *
 * @author hiwepy
 * @since 4.0.0
 * @see TransactionPort
 * @see TransactionContext
 * @see io.ddd4j.core.constant.SpiKeys#TRANSACTION_MANAGER
 */
public interface TransactionManager {

    /**
     * 开启全局分布式事务。
     *
     * @param name      事务名称（用于日志和监控）
     * @param timeoutMs 超时时间（毫秒）
     * @return 事务上下文（含 xid）
     * @throws TransactionException 开启事务失败
     */
    TransactionContext begin(String name, int timeoutMs);

    /**
     * 开启全局分布式事务（默认超时 60s）。
     *
     * @param name 事务名称
     * @return 事务上下文（含 xid）
     * @throws TransactionException 开启事务失败
     */
    default TransactionContext begin(String name) {
        return begin(name, 60_000);
    }

    /**
     * 提交全局分布式事务。
     *
     * @param context 事务上下文
     * @throws TransactionException 提交事务失败
     */
    void commit(TransactionContext context);

    /**
     * 回滚全局分布式事务。
     *
     * @param context 事务上下文
     * @throws TransactionException 回滚事务失败
     */
    void rollback(TransactionContext context);

    /**
     * 获取当前全局事务状态。
     *
     * @return 事务状态
     */
    TransactionStatus getStatus();

    /**
     * 获取当前全局事务 ID（XID）。
     *
     * <p>如果当前线程没有活跃事务，返回 {@code null}。
     *
     * @return XID 或 null
     */
    String getXid();

    /**
     * 挂起当前事务并返回事务上下句柄。
     *
     * <p>用于嵌套事务场景：先挂起外层事务，执行内层事务，再恢复外层。
     *
     * @return 挂起的事务上下文，如果没有活跃事务则返回 null
     */
    default TransactionContext suspend() {
        return null;
    }

    /**
     * 恢复之前挂起的事务。
     *
     * @param context 挂起时返回的事务上下文
     */
    default void resume(TransactionContext context) {
        // default no-op
    }
}
