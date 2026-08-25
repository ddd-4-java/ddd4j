package io.ddd4j.data.cqrs.sample;

import io.ddd4j.core.cqrs.command.Command;

/**
 * 事务探针命令（集成测试专用）：触发
 * {@code TxProbeCommandHandler} 在分发路径上采样事务状态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class TxProbeCommand implements Command {
}
