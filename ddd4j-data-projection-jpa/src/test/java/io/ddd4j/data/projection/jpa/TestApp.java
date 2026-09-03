package io.ddd4j.data.projection.jpa;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * 集成测试引导应用（Task 7.2，H2 内存库 IT 用）。
 *
 * <p>刻意不使用 {@code @SpringBootApplication}：本模块不依赖 Boot 运行时，
 * 测试栈以最小装配组合出真实容器——自动配置（DataSource/JPA/事务管理）＋
 * {@code @EntityScan} 扫描 {@link ProjectionPositionEntity}＋组件扫描
 * {@link JpaProjectionPositionRepository}，Spring Data 仓储接口由 JpaRepositories
 * 自动配置从本类所在包向下发现 {@link SpringDataProjectionPositionRepository}
 * （与 {@code ddd4j-data-event-store-jpa} 的 TestApp 同款模式）。
 *
 * <p>数据源由 {@code application-test.yml}（H2 内存库，MODE=PostgreSQL）提供。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "io.ddd4j.data.projection.jpa")
@ComponentScan(basePackages = "io.ddd4j.data.projection.jpa")
class TestApp {
}
