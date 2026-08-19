package io.ddd4j.tx;

/**
 * TCC（Try-Confirm-Cancel）模式资源操作接口。
 *
 * <p>业务方实现此接口，定义三个阶段的业务逻辑：
 * <ul>
 *   <li><b>try</b>：预留资源（如冻结库存、预扣余额）</li>
 *   <li><b>confirm</b>：确认提交（如扣减冻结库存、确认扣款）</li>
 *   <li><b>cancel</b>：回滚释放（如解冻库存、退回余额）</li>
 * </ul>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>幂等性</b>：confirm 和 cancel 必须幂等（网络重试不会产生副作用）</li>
 *   <li><b>空回滚保护</b>：cancel 在 try 未执行时也不应报错</li>
 *   <li><b>悬挂处理</b>：cancel 先于 try 到达时，try 应拒绝执行</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class InventoryTccAction implements TccAction {
 *
 *     @Override
 *     public boolean tryAction(TransactionContext ctx) {
 *         String sku = ctx.getBusinessParam("sku");
 *         int quantity = ctx.getBusinessParam("quantity");
 *         return inventoryService.freeze(sku, quantity);
 *     }
 *
 *     @Override
 *     public void confirmAction(TransactionContext ctx) {
 *         String sku = ctx.getBusinessParam("sku");
 *         int quantity = ctx.getBusinessParam("quantity");
 *         inventoryService.deduct(sku, quantity);
 *     }
 *
 *     @Override
 *     public void cancelAction(TransactionContext ctx) {
 *         String sku = ctx.getBusinessParam("sku");
 *         int quantity = ctx.getBusinessParam("quantity");
 *         inventoryService.unfreeze(sku, quantity);
 *     }
 * }
 * }</pre>
 *
 * @author hiwepy
 * @since 4.0.0
 * @see TransactionManager
 * @see TransactionContext
 */
public interface TccAction {

    /**
     * Try 阶段：预留资源。
     *
     * <p>实现要求：
     * <ul>
     *   <li>检查资源是否可用</li>
     *   <li>预留资源（如冻结库存）</li>
     *   <li>返回 true 表示 try 成功，false 表示需要回滚</li>
     *   <li>抛出异常等同于返回 false</li>
     * </ul>
     *
     * @param context 事务上下文（含业务参数）
     * @return true 表示 try 成功，false 表示需要回滚
     * @throws Exception 业务异常
     */
    boolean tryAction(TransactionContext context) throws Exception;

    /**
     * Confirm 阶段：确认提交。
     *
     * <p>仅当所有分支的 try 都成功后，TC 才会驱动此方法。
     *
     * <p>实现要求：
     * <ul>
     *   <li><b>幂等</b>：网络重试不会产生副作用</li>
     *   <li>将预留资源转为实际扣减</li>
     * </ul>
     *
     * @param context 事务上下文
     * @throws Exception 业务异常
     */
    void confirmAction(TransactionContext context) throws Exception;

    /**
     * Cancel 阶段：回滚释放。
     *
     * <p>任一分支 try 失败或超时后，TC 驱动所有已成功的分支执行 cancel。
     *
     * <p>实现要求：
     * <ul>
     *   <li><b>幂等</b>：网络重试不会产生副作用</li>
     *   <li><b>空回滚保护</b>：try 未执行时 cancel 也不应报错</li>
     *   <li>释放预留资源</li>
     * </ul>
     *
     * @param context 事务上下文
     * @throws Exception 业务异常
     */
    void cancelAction(TransactionContext context) throws Exception;
}
