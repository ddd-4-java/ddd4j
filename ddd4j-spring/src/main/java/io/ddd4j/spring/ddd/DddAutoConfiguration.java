package io.ddd4j.spring.ddd;

import java.util.List;
import java.util.Objects;

import org.fuin.cqrs4j.core.MultiCommandExecutor;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.mem.InMemoryEventStore;
import org.fuin.cqrs4j.core.CommandExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * ddd4j-ddd 自动配置。
 *
 * <p>当 classpath 存在 {@link org.fuin.ddd4j.core.AggregateRoot} 时自动激活，提供：
 * <ul>
 *   <li>{@link EventStore} — 默认使用 {@link InMemoryEventStore}（esc-mem，开发/测试用，无需部署 EventStoreDB）</li>
 *   <li>{@link Ddd4JacksonModule} — 领域事件/命令的 Jackson 序列化支持</li>
 *   <li>{@link org.fuin.cqrs4j.core.MultiCommandExecutor} — 命令总线（自动扫描所有 {@code CommandExecutor} Bean）</li>
 * </ul>
 *
 * <p>配置项（{@code application.yml}）：
 * <pre>
 * ddd4j:
 *   ddd:
 *     eventstore:
 *       type: mem | kurrent  # 默认 mem
 * </pre>
 *
 * <p>生产环境切换到 KurrentDB 时，需自行注入 {@code EventStore} Bean（参考 esc-esgrpc 模块）。
 *
 * @author wandl
 * @since 3.4.x
 */
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties(DddProperties.class)
public class DddAutoConfiguration {

    /**
     * 默认事件存储：内存版（开发/测试用）。
     *
     * <p>当 {@code ddd4j.ddd.eventstore.type=mem}（默认）或未配置时激活。
     * 生产环境应配置为 {@code kurrent} 并自行注入 KurrentDB 的 EventStore Bean。
     *
     * <p>返回前自动调用 {@code open()} 激活存储。
     */
    @Bean
    public EventStore inMemoryEventStore() {
        InMemoryEventStore store = new InMemoryEventStore(Runnable::run);
        store.open();
        return store;
    }

    /**
     * DDD 的 Jackson 序列化模块。
     *
     * <p>注册到 ObjectMapper 后，领域事件（{@link org.fuin.ddd4j.core.DomainEvent}）
     * 和命令（{@link org.fuin.cqrs4j.core.Command}）可正确序列化/反序列化。
     *
     * <p>需要 {@link org.fuin.ddd4j.core.EntityIdFactory} 来按类型字符串创建标识值对象。
     * 默认使用 {@link org.fuin.ddd4j.core.JandexEntityIdFactory}（基于 Jandex 字节码索引自动发现）。
     */
    @Bean
    public Ddd4JacksonModule ddd4JacksonModule(ObjectProvider<EntityIdFactory> entityIdFactoryProvider) {
        EntityIdFactory factory = entityIdFactoryProvider.getIfAvailable();
        if (Objects.isNull(factory)) {
            factory = new JandexEntityIdFactory();
        }
        return new Ddd4JacksonModule(factory);
    }

    /**
     * 命令总线：自动扫描所有 {@link CommandExecutor} Bean 并按 EventType 路由。
     *
     * <p>当 classpath 中存在 {@link org.fuin.cqrs4j.core.Command} 时激活。
     * 用户提交命令 → {@code bus.execute(ctx, cmd)} → 按 {@code cmd.getEventType()} 路由到对应执行器。
     */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public MultiCommandExecutor dddCommandBus(ObjectProvider<List<CommandExecutor>> executorsProvider) {
        List<CommandExecutor> executors = executorsProvider.getIfAvailable(List::of);
        return new MultiCommandExecutor(executors);
    }

}
