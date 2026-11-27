package io.ddd4j.sample.quarkus;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;
import java.util.Objects;

/**
 * 管理商品交易测试数据库的生命周期并向测试注入连接配置。
 */
public final class CatalogDatabaseResource implements QuarkusTestResourceLifecycleManager {
    /**
     * 提供独立 MySQL 实例的测试容器。
     */
    private MySQLContainer<?> mysql;

    /**
     * 根据本次调用更新 <code>mysql = new MySQLContainer&lt;&gt;("mysql:8.0.36")</code>，用于后续测试步骤。
     *
     * @return 提供给后续契约断言的 <code>Map&lt;String, String&gt;</code> 结果
     */
    @Override
    public Map<String, String> start() {
        mysql = new MySQLContainer<>("mysql:8.0.36");
        mysql.start();
        return Map.of("catalog.jdbc.url", mysql.getJdbcUrl(), "catalog.db.user", mysql.getUsername(),
                "catalog.db.password", mysql.getPassword());
    }

    /**
     * 调用 <code>mysql.stop()</code> 执行当前夹具操作。
     */
    @Override
    public void stop() {
        if (Objects.nonNull(mysql)) {
            mysql.stop();
        }
    }
}
