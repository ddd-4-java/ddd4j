package io.ddd4j.sample.order.infrastructure;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
import io.ddd4j.sample.order.domain.exception.DuplicateTradeKey;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.infrastructure.persistence.CatalogMapper;
import io.ddd4j.sample.order.infrastructure.persistence.MybatisRepositories;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;

/**
 * 四表初始化和 MyBatis 事务工厂。
 */
public final class CatalogDatabase {
    /**
     * 限制工具类实例化。
     */
    private CatalogDatabase() {
    }

    /**
     * 初始化四表数据库并创建租户事务入口。
     *
     * @param url      MySQL JDBC URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 租户事务入口
     */
    public static TradeSession.Transactions open(String url, String username, String password) {
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("CATALOG_JDBC_URL 必须指定专用 MySQL 数据库");
        }
        var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
        return open(source);
    }

    /**
     * 初始化四表数据库并创建租户事务入口。
     *
     * @param source 用于四表初始化及后续事务的数据源
     * @return 租户事务入口
     */
    public static TradeSession.Transactions open(DataSource source) {
        initializeSchema(source);
        Configuration config = new Configuration(new Environment("catalog", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        // Mapper SQL already binds the transaction tenant and preserves the locking clauses.
        config.addInterceptor(new Ddd4jMybatisConfigurer().build());
        config.addMapper(CatalogMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        var tenantContext = new Ddd4jTenantContext();
        return new TradeSession.Transactions() {
            @Override
            public <T> T execute(String tenant, Function<TradeSession, T> work) {
                try (var transactionContext = ThreadContext.open();
                     var scope = tenantContext.open(Objects.requireNonNull(tenant, "tenant"));
                     SqlSession session = factory.openSession(false)) {
                    T result = work.apply(new MybatisRepositories(session.getMapper(CatalogMapper.class), tenant));
                    session.commit();
                    return result;
                } catch (PersistenceException failure) {
                    throw translate(failure);
                }
            }
        };
    }

    /**
     * 按脚本中的建表顺序补齐缺失的商品交易表。
     *
     * @param source 商品交易数据库的数据源
     * @throws IllegalStateException 无法读取建表脚本或获取数据库连接
     */
    private static void initializeSchema(DataSource source) {
        try (var script = Resources.getResourceAsStream("db/catalog-trade/V1_0_0__catalog_trade.sql");
             var connection = source.getConnection()) {
            // 保留原始 DDL。启动时按父子顺序补建缺失的表，兼容重复启动和部分初始化。
            var ddl = new String(script.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");
            var runner = new ScriptRunner(connection);
            runner.setAutoCommit(true);
            runner.setStopOnError(true);
            runner.setLogWriter(null);
            runner.setErrorLogWriter(null);
            runner.runScript(new StringReader(ddl));
        } catch (IOException | SQLException failure) {
            throw new IllegalStateException("商品交易表初始化失败", failure);
        }
    }

    /**
     * 将 MySQL 唯一键冲突转换成业务重复异常。
     *
     * @param failure MyBatis 抛出的持久化异常
     * @return 重复业务键异常，或未匹配时的原始异常
     */
    private static RuntimeException translate(PersistenceException failure) {
        for (Throwable cause = failure; Objects.nonNull(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return new DuplicateTradeKey(failure);
            }
        }
        return failure;
    }
}
