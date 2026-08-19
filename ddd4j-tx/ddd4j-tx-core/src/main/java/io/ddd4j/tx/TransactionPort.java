package io.ddd4j.tx;

import java.util.Objects;

/**
 * 分布式事务边界端口（对齐 {@code OrderTransactionPort}）。
 *
 * <p>应用服务通过此接口定义事务边界，运行时适配器注入具体实现：
 * <ul>
 *   <li><b>本地事务</b>：JDBC Connection 管理（{@code JdbcTransactionPort}）</li>
 *   <li><b>TCC</b>：try → confirm/cancel 二阶段（{@code TccTransactionPort}）</li>
 *   <li><b>AT</b>：自动代理 DataSource（{@code AtTransactionPort}）</li>
 *   <li><b>Saga</b>：状态机驱动 + 补偿（{@code SagaTransactionPort}）</li>
 * </ul>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>零框架依赖——仅依赖 Java 标准库</li>
 *   <li>方法签名对齐 {@code OrderTransactionPort#execute(Runnable)}，降低迁移成本</li>
 *   <li>支持嵌套事务（通过 {@link TransactionManager#suspend()} / {@link TransactionManager#resume(TransactionContext)}）</li>
 * </ul>
 *
 * <h3>SPI 注册</h3>
 * <pre>{@code
 * // 框架适配层启动期注册
 * Contexts.register(SpiKeys.TRANSACTION_PORT, TransactionPort.class, jdbcPort);
 *
 * // 业务方使用
 * TransactionPort port = Contexts.getOrThrow(SpiKeys.TRANSACTION_PORT, TransactionPort.class);
 * port.execute(() -> {
 *     order.save();
 *     inventory.deduct(sku, quantity);
 * });
 * }</pre>
 *
 * @author hiwepy
 * @since 4.0.0
 * @see TransactionManager
 * @see io.ddd4j.core.constant.SpiKeys#TRANSACTION_PORT
 */
@FunctionalInterface
public interface TransactionPort {

    /**
     * 在事务边界内执行操作。
     *
     * <p>实现保证：
     * <ul>
     *   <li>操作内的所有数据变更在同一事务中</li>
     *   <li>操作成功后事务提交</li>
     *   <li>操作抛出异常后事务回滚</li>
     *   <li>支持嵌套调用（外层事务未提交时，内层复用同一事务）</li>
     * </ul>
     *
     * @param operation 事务内的业务操作
     * @throws TransactionException 事务执行失败
     */
    void execute(Runnable operation);

    /**
     * 返回用于非事务性测试或最小化运行时的直通实现。
     *
     * @return 不开启实际事务的事务端口
     */
    static TransactionPort noop() {
        return operation -> Objects.requireNonNull(operation, "operation must not be null").run();
    }
}
