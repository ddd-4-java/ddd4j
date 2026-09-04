package io.ddd4j.data.cqrs.sample;

import java.util.Collections;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事务探针处理器（集成测试专用）：在 execute 内采样
 * {@link TransactionSynchronizationManager#isActualTransactionActive()}——
 * 直接证明（或证伪）「命令分发被事务代理包裹」：若方法级 {@code @Transactional}
 * 生效，Handler 在事务边界内执行，采样为 true；若注解失效（历史缺陷：类级
 * 注解对继承方法不生效），无代理／无事务，采样为 false。
 *
 * <p>采样用静态持有器：Handler 实例由容器管理，IT 只能跨实例读取
 * （单测线程内 execute 先行发生于断言）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Component
@CommandHandler(TxProbeCommand.class)
public class TxProbeCommandHandler implements CommandExecutor<TxProbeCommand> {

    /**
     * 最近一次分发是否处于活动事务（每次 execute 覆写）。
     */
    public static final AtomicBoolean DISPATCHED_IN_ACTIVE_TX = new AtomicBoolean(false);

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Collections.singleton(TxProbeCommand.class);
    }

    @Override
    public Result execute(TxProbeCommand command) {
        DISPATCHED_IN_ACTIVE_TX.set(TransactionSynchronizationManager.isActualTransactionActive());
        return Result.ok();
    }
}
