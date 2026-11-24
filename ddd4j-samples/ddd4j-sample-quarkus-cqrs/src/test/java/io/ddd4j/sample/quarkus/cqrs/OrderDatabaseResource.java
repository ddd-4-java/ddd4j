package io.ddd4j.sample.quarkus.cqrs;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 隔离 PostgreSQL，仅运行仓库已有迁移和既有 JPA 实体建表。 */
public class OrderDatabaseResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"));
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
        Map<String, String> configuration = new LinkedHashMap<>();
        configuration.put("quarkus.datasource.jdbc.url", postgres.getJdbcUrl());
        configuration.put("quarkus.datasource.username", postgres.getUsername());
        configuration.put("quarkus.datasource.password", postgres.getPassword());
        configuration.put("quarkus.hibernate-orm.database.generation", "drop-and-create");
        return Collections.unmodifiableMap(configuration);
    }

    @Override
    public void stop() {
        if (Objects.nonNull(postgres)) {
            postgres.stop();
        }
    }
}
