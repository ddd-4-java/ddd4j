package io.ddd4j.spring.ddd;

import io.ddd4j.spring.ddd.scanner.DddClassPathBeanDefinitionScanner;
import org.fuin.cqrs4j.core.CommandExecutor;
import org.fuin.cqrs4j.core.MultiCommandExecutor;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.esc.api.EventStore;
import org.fuin.esc.mem.InMemoryEventStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Objects;

/**
 * ddd4j-ddd 自动配置。
 *
 * <p><b>迁移说明</b>：自 2.0.x 起，本类将从 {@code ddd4j-spring} 下移到
 * {@code ddd4j-boot-ddd}（Spring Boot starter），届时将加上 {@code @AutoConfiguration} 注解
 * 并通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 注册。
 * 新业务请直接依赖 {@code ddd4j-boot-ddd} 配合 {@code @EnableDdd4j} 注解使用。
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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 * @deprecated 自 2.0.x 起下移到 {@code ddd4j-boot-ddd.config.DddAutoConfiguration}，
 * 使用 {@code @EnableDdd4j} 注解启用
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
// @EnableConfigurationProperties(DddProperties.class)
public class DddAutoConfiguration {

    /**
     * DDD 注解扫描器 Bean：让 Spring 自动识别纯 Java 的 DDD 构造型注解
     * （{@code @DomainService}、{@code @DomainRepository}、{@code @ApplicationService} 等）。
     *
     * <p>业务项目通过 {@code @ComponentScan(basePackages = "com.example.domain")} 触发扫描。
     * 本 Bean 确保 DDD 注解（无 {@code @Service}/{@code @Repository} 元注解）也能被 Spring 识别。
     *
     * <p>如果需要自定义扫描路径，可在 application.yml 中配置：
     * <pre>
     * ddd4j:
     *   ddd:
     *     scan-base-packages: com.example.domain,com.example.application
     * </pre>
     */
    @Bean
    public static DddClassPathBeanDefinitionScanner dddAnnotationScanner(
            BeanDefinitionRegistry registry,
            Environment environment) {
        DddClassPathBeanDefinitionScanner scanner = new DddClassPathBeanDefinitionScanner(registry);
        // 从配置中读取扫描路径，默认为空（由业务项目的 @ComponentScan 控制）
        String configuredPackages = environment.getProperty("ddd4j.ddd.scan-base-packages", "");
        if (!configuredPackages.isEmpty()) {
            String[] packages = configuredPackages.split(",");
            for (String pkg : packages) {
                scanner.scan(pkg.trim());
            }
        }
        return scanner;
    }

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
