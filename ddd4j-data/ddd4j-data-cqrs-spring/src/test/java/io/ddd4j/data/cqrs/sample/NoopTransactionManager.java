package io.ddd4j.data.cqrs.sample;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 无资源事务管理器（集成测试专用，Task 6.3 修复轮）：真实走
 * {@link AbstractPlatformTransactionManager} 的 begin/commit/rollback 生命周期
 * （{@code prepareSynchronization} 会在 begin 时置位
 * {@code TransactionSynchronizationManager.isActualTransactionActive()}），
 * 但不管理任何实际资源——不引 H2/DataSource 即可让
 * {@code TransactionAutoConfiguration} 激活 {@code @Transactional} 代理，
 * 供 {@code SpringCommandBusIT} 验证方法级事务注解真实生效。
 * spring-tx 6.x 已移除旧的 {@code ResourcelessTransactionManager}，此为等价的
 * 最小测试替身（约 20 行，真 TM 语义非 Mockito mock——mock 绕过
 * {@code getTransaction} 真实流程，置不了事务激活标志）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class NoopTransactionManager extends AbstractPlatformTransactionManager {

    NoopTransactionManager() {
        // 默认 SYNCHRONIZATION_ALWAYS：begin 即初始化同步并置活动事务标志
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 无资源：事务状态由父类簿记，无需动作
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // 无资源：提交为空操作
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // 无资源：回滚为空操作
    }
}
