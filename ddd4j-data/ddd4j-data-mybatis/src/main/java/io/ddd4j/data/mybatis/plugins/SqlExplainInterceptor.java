package io.ddd4j.data.mybatis.plugins;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Objects;

/**
 * 慢 SQL 监控拦截器（零 MyBatis-Plus 依赖）。
 *
 * <p>监控超过阈值的长 SQL，支持自定义告警回调。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class SqlExplainInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlExplainInterceptor.class);

    /**
     * 长 SQL 阈值（字符数），默认 2000
     */
    private int longSqlThreshold = 2000;

    private SqlSlowLogger slowLogger;

    public SqlExplainInterceptor() {
    }

    public void setSlowLogger(SqlSlowLogger slowLogger) {
        this.slowLogger = slowLogger;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler sh = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(sh);
        MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (Objects.isNull(ms)) {
            return invocation.proceed();
        }

        BoundSql boundSql = sh.getBoundSql();
        String sql = Objects.nonNull(boundSql) ? boundSql.getSql() : null;
        if (Objects.nonNull(sql) && sql.length() > longSqlThreshold) {
            log.warn("Long SQL detected [length={}, mapper={}]: {}", sql.length(),
                    ms.getId(), sql.substring(0, Math.min(200, sql.length())) + "...");
            if (slowLogger != null) {
                slowLogger.onSlow(ms.getId(), sql, 0);
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    /**
     * 慢 SQL 记录回调接口。
     */
    @FunctionalInterface
    public interface SqlSlowLogger {
        void onSlow(String mappedStatementId, String sql, long elapsedMs);
    }
}
