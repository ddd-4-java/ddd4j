package io.ddd4j.sample.auth.multilogin.config;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import io.ddd4j.cache.subject.InMemorySubject;
import io.ddd4j.cache.subject.InMemorySubjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sample 启动期注册 InMemorySubject 到 ddd4j SPI。
 *
 * <p>不耦合任何具体框架的事件发布 API（Spring ApplicationEventPublisher / CDI Event / Guava EventBus）：
 * 业务方统一通过 ddd4j {@link DomainEventPublisher} SPI 解耦。
 *
 * <p>Spring 适配层（{@code ddd4j-runtime-spring}）会负责把
 * {@code DomainEvent<LoginSucceededEvent>} 桥接为 Spring {@code @EventListener} 能监听到的事件。
 */
@Configuration
public class MultiLoginSubjectConfig {

    /**
     * 创建基于内存的 Subject 实例，注入领域事件发布器。
     *
     * @return InMemorySubject 实例
     */
    @Bean
    public InMemorySubject inMemorySubject() {
        DomainEventPublisher eventPublisher = Contexts.injectOrThrow(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        return new InMemorySubject(eventPublisher);
    }

    /**
     * 注册 SubjectProvider 到 ddd4j SPI 上下文。
     *
     * @param subject InMemorySubject 实例
     * @return SubjectProvider 实例
     */
    @Bean
    public SubjectProvider subjectProvider(InMemorySubject subject) {
        SubjectProvider provider = new InMemorySubjectProvider(subject);
        SubjectKit.register(provider);
        return provider;
    }
}
