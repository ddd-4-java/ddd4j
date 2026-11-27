package io.ddd4j.sample.order.infrastructure;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
import io.ddd4j.data.mybatis.config.Ddd4jMybatisConfigurer;
import cn.hutool.core.io.IoUtil;
import java.sql.Connection;
import java.io.InputStream;
import io.ddd4j.sample.order.domain.gateway.TradeSession;
import io.ddd4j.sample.order.infrastructure.persistence.CatalogMapper;
import io.ddd4j.sample.order.infrastructure.persistence.MybatisRepositories;
import java.util.function.Function;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.exceptions.PersistenceException;
import io.ddd4j.sample.order.domain.gateway.DuplicateTradeKey;

/** 四表初始化和 MyBatis 事务工厂。 */
public final class CatalogDatabase {
    /** \u9650\u5236\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private CatalogDatabase() {
    }

    /**
     * 初始化四表数据库并创建租户事务入口。
     *
     * @param url MySQL JDBC URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 租户事务入口
     */
    public static TradeSession.Transactions open(String url, String username, String password) {
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("CATALOG_JDBC_URL 必须指定专用 MySQL 数据库");
        }
        UnpooledDataSource source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
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
        config.addInterceptor(new Ddd4jMybatisConfigurer().build());
        config.addMapper(CatalogMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        Ddd4jTenantContext tenantContext = new Ddd4jTenantContext();
        return new TradeSession.Transactions() {
            @Override
            public <T> T execute(String tenant, Function<TradeSession, T> work) {
                try (ThreadContext.Scope transactionContext = ThreadContext.open();
                     Ddd4jTenantContext.Scope scope = tenantContext.open(Objects.requireNonNull(tenant, "tenant"));
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
     * \u6309\u811a\u672c\u4e2d\u7684\u5efa\u8868\u987a\u5e8f\u8865\u9f50\u7f3a\u5931\u7684\u5546\u54c1\u4ea4\u6613\u8868\u3002
     * @param source \u5546\u54c1\u4ea4\u6613\u6570\u636e\u5e93\u7684\u6570\u636e\u6e90
     * @throws IllegalStateException \u65e0\u6cd5\u8bfb\u53d6\u5efa\u8868\u811a\u672c\u6216\u83b7\u53d6\u6570\u636e\u5e93\u8fde\u63a5
     */
    private static void initializeSchema(DataSource source) {
        try (InputStream script = Resources.getResourceAsStream("db/catalog-trade/V1_0_0__catalog_trade.sql");
             Connection connection = source.getConnection()) {
            // 保留原始 DDL。启动时按父子顺序补建缺失的表，兼容重复启动和部分初始化。
            String ddl = IoUtil.read(script, StandardCharsets.UTF_8)
                    .replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ");
            ScriptRunner runner = new ScriptRunner(connection);
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
     * \u5c06 MySQL \u552f\u4e00\u952e\u51b2\u7a81\u8f6c\u6362\u6210\u4e1a\u52a1\u91cd\u590d\u5f02\u5e38\u3002
     * @param failure MyBatis \u629b\u51fa\u7684\u6301\u4e45\u5316\u5f02\u5e38
     * @return \u91cd\u590d\u4e1a\u52a1\u952e\u5f02\u5e38\uff0c\u6216\u672a\u5339\u914d\u65f6\u7684\u539f\u59cb\u5f02\u5e38
     */
    private static RuntimeException translate(PersistenceException failure) {
        for (Throwable cause = failure; Objects.nonNull(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException && ((SQLException) cause).getErrorCode() == 1062) {
                return new DuplicateTradeKey(failure);
            }
        }
        return failure;
    }
}
