package io.ddd4j.sample.quarkus.satoken;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;
import java.util.Objects;

/** \u7ba1\u7406\u5546\u54c1\u4ea4\u6613\u6d4b\u8bd5\u6570\u636e\u5e93\u7684\u751f\u547d\u5468\u671f\u5e76\u5411\u6d4b\u8bd5\u6ce8\u5165\u8fde\u63a5\u914d\u7f6e\u3002 */
public final class CatalogDatabaseResource implements QuarkusTestResourceLifecycleManager {
    /** \u63d0\u4f9b\u72ec\u7acb MySQL \u5b9e\u4f8b\u7684\u6d4b\u8bd5\u5bb9\u5668\u3002 */
    private MySQLContainer<?> mysql;

    /**
     * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>mysql = new MySQLContainer&lt;&gt;("mysql:8.0.36")</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>Map&lt;String, String&gt;</code> \u7ed3\u679c
     */
    @Override
    public Map<String, String> start() {
        mysql = new MySQLContainer<>("mysql:8.0.36");
        mysql.start();
        return ImmutableMap.of("catalog.jdbc.url", mysql.getJdbcUrl(), "catalog.db.user", mysql.getUsername(),
                "catalog.db.password", mysql.getPassword());
    }

    /** \u8c03\u7528 <code>mysql.stop()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
    @Override
    public void stop() {
        if (Objects.nonNull(mysql)) {
            mysql.stop();
        }
    }
}
