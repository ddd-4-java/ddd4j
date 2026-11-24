package io.ddd4j.sample.spring;

import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 使用既有 SQL 夹具和 JDBC 适配器验证真实 PostgreSQL 写入及容器关闭重开。 */
class SpringOrderPostgresTest {
    @Test
    void defaultPersistenceSurvivesApplicationReopen() {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            String id;
            ConfigurableApplicationContext context = start(postgres);
            try (context) {
                var driver = driver(context);
                SampleContractAssertions.defaultOrder(driver, "default-postgres");
                id = driver.create(new CreateOrderCommand("reopen", "buyer-1", "Alice"));
            }
            assertFalse(context.isActive());
            ConfigurableApplicationContext reopened = start(postgres);
            try (reopened) {
                assertEquals("reopen", driver(reopened).find(id).orderNo());
            }
            assertFalse(reopened.isActive());
        }
    }
    private ConfigurableApplicationContext start(PostgreSQLContainer<?> postgres) {
        return SpringApplication.run(SpringSampleApplication.class, "--server.port=0", "--spring.profiles.active=postgres",
                "--sample.jdbc.url=" + postgres.getJdbcUrl(), "--sample.jdbc.username=" + postgres.getUsername(),
                "--sample.jdbc.password=" + postgres.getPassword());
    }
    private HttpOrderSampleDriver driver(ConfigurableApplicationContext context) {
        return new HttpOrderSampleDriver("http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort());
    }
}
