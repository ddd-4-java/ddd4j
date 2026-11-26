package io.ddd4j.sample.quarkus.cqrs;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;
import java.util.Objects;

public final class CatalogDatabaseResource implements QuarkusTestResourceLifecycleManager {
    private MySQLContainer<?> mysql;

    @Override
    public Map<String, String> start() {
        mysql = new MySQLContainer<>("mysql:8.0.36");
        mysql.start();
        return Map.of("catalog.jdbc.url", mysql.getJdbcUrl(), "catalog.db.user", mysql.getUsername(),
                "catalog.db.password", mysql.getPassword());
    }

    @Override
    public void stop() {
        if (Objects.nonNull(mysql)) {
            mysql.stop();
        }
    }
}
