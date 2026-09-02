package io.ddd4j.data.eventstore.jpa;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.cqrs.eventstore.jackson.EventPayloadSerializer;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * 集成测试引导应用（Task 4.4，H2 全量契约轨与 PG 容器 CI 轨共用）。
 *
 * <p>刻意不使用 {@code @SpringBootApplication}：本模块不依赖 Boot 运行时，
 * 测试栈以最小装配组合出真实容器——自动配置（DataSource/JPA/事务管理）＋
 * {@code @EntityScan} 扫描 {@link StoredEventEntity}＋组件扫描 {@link JpaEventStore}，
 * Spring Data 仓储接口由 JpaRepositories 自动配置从本类所在包向下发现
 * {@link SpringDataStoredEventRepository}。
 *
 * <p>{@link EventPayloadSerializer} 是纯类（无容器注解，ADR-0005），此处按
 * {@link JpaEventStore} javadoc 的集成方装配方式注册真实 Bean：
 * mapper 以 {@code findAndAddModules} 构建（classpath 上的 JavaTimeModule 等被
 * ServiceLoader 发现），序列化/反序列化零 mock——这正是集成测试要验的真实往返。
 *
 * <p>数据源由 {@code application-test.yml}（H2 内存库，MODE=PostgreSQL）提供；
 * PG 轨经 {@code @DynamicPropertySource} 覆盖 JDBC 三项连接属性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "io.ddd4j.data.eventstore.jpa")
@ComponentScan(basePackages = "io.ddd4j.data.eventstore.jpa")
class TestApp {

    /**
     * 注册真实的事件 payload 序列化器（mapper 经 findAndAddModules 构建，非 mock）。
     *
     * @return 序列化器 Bean
     */
    @Bean
    EventPayloadSerializer eventPayloadSerializer() {
        return new EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build());
    }
}
