package io.ddd4j.core.constant;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;

/**
 * ddd4j SPI 服务约定 key 常量。
 * <p>
 * 业务方通过 {@link BaseContext#inject(String, Class)} 或
 * {@link ThreadContext#inject(String, Class)} 按这些 key 查找 SPI 服务实例。
 *
 * <h3>使用约定</h3>
 * <ul>
 *   <li><b>框架适配层</b>：在启动期按这些 key 注入 SPI 服务实例（Kafka / RabbitMQ / CDI Bean / Guice Provider）</li>
 *   <li><b>业务代码</b>：通过 key 查找 SPI 服务，零框架耦合</li>
 *   <li><b>单测</b>：直接 {@code BaseContext.inject(KEY, type, mockImpl)} 注入 mock</li>
 * </ul>
 *
 * <h3>查找优先级</h3>
 * <ol>
 *   <li>{@link ThreadContext}（线程级，请求级 SPI 覆盖）</li>
 *   <li>{@link BaseContext}（JVM 级，全局默认 SPI）</li>
 * </ol>
 *
 * <h3>命名规范</h3>
 * key 统一以 {@code ddd4j.spi.&lt;领域&gt;.&lt;服务名&gt;} 格式命名，避免冲突。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public final class SpiKeys {

    private SpiKeys() {
    }

    /** SPI 根命名空间前缀 */
    public static final String PREFIX = "ddd4j.spi.";

    // ========== 事件相关 SPI ==========

    /** MQ 事件发布者 SPI key，对应 {@code io.ddd4j.core.event.MQEventPublisher} */
    public static final String MQ_EVENT_PUBLISHER = PREFIX + "mq.MQEventPublisher";

    /** 进程内领域事件发布者 SPI key，对应 {@code io.ddd4j.core.ddd.event.DomainEventPublisher} */
    public static final String DOMAIN_EVENT_PUBLISHER = PREFIX + "domain.DomainEventPublisher";

    /** 认证主体提供者 SPI key，对应 {@code io.ddd4j.core.subject.SubjectProvider} */
    public static final String SUBJECT_PROVIDER = PREFIX + "security.SubjectProvider";

    /** 国际化提供者 SPI key，对应 {@code io.ddd4j.core.i18n.I18nProvider} */
    public static final String I18N_PROVIDER = PREFIX + "i18n.I18nProvider";

    // ========== CQRS 相关 SPI（预留） ==========

    /** 命令执行器注册表 SPI key，对应 {@code io.ddd4j.core.cqrs.query.CommandExecutorRegistry} */
    public static final String COMMAND_EXECUTOR_REGISTRY = PREFIX + "cqrs.CommandExecutorRegistry";

    /** 投影位置持久化 SPI key，对应 {@code io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository} */
    public static final String PROJECTION_POSITION_REPOSITORY = PREFIX + "cqrs.ProjectionPositionRepository";

    // ========== 数据相关 SPI ==========

    /** 仓储注册表 SPI key，对应 {@code io.ddd4j.core.ddd.repository.Repository}（替代旧静态实例表） */
    public static final String REPOSITORY_REGISTRY = PREFIX + "data.RepositoryRegistry";
}
