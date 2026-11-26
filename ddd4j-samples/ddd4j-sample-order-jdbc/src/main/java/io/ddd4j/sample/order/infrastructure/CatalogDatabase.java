package io.ddd4j.sample.order.infrastructure;

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
    private CatalogDatabase() {
    }

    public static TradeSession.Transactions open(String url, String username, String password) {
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("CATALOG_JDBC_URL 必须指定专用 MySQL 数据库");
        }
        var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
        return open(source);
    }

    public static TradeSession.Transactions open(DataSource source) {
        initializeSchema(source);
        Configuration config = new Configuration(new Environment("catalog", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(CatalogMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        return new TradeSession.Transactions() {
            @Override
            public <T> T execute(String tenant, Function<TradeSession, T> work) {
                try (SqlSession session = factory.openSession(false)) {
                    T result = work.apply(new MybatisRepositories(session.getMapper(CatalogMapper.class), tenant));
                    session.commit();
                    return result;
                } catch (PersistenceException failure) {
                    throw translate(failure);
                }
            }
        };
    }

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

    private static RuntimeException translate(PersistenceException failure) {
        for (Throwable cause = failure; Objects.nonNull(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                return new DuplicateTradeKey(failure);
            }
        }
        return failure;
    }
}
