package io.ddd4j.data.mybatis.plugins;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.sql.Connection;
import java.util.Objects;

/**
 * INSERT IGNORE 改写插件（零 MyBatis-Plus 依赖）。
 *
 * <p>MyBatis StatementHandler 拦截器，将 INSERT 语句改写为 INSERT IGNORE。
 * 通过 {@link ThreadLocal} 开关控制，仅在实际需要时启用。
 *
 * <p>典型场景：配合唯一索引实现幂等插入——重复插入时静默忽略而不抛异常。
 *
 * <p>使用方式：
 * <pre>
 * // 注册插件（Spring Boot / Guice / CDI）
 * InsertIgnorePlugin.enable();
 * try {
 *     repository.save(model);
 * } finally {
 *     InsertIgnorePlugin.reset(); // 必须在 finally 中重置
 * }
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class InsertIgnorePlugin implements Interceptor {

    private static final ThreadLocal<Boolean> THREAD_LOCAL = new ThreadLocal<>();

    public static void enable() {
        THREAD_LOCAL.set(Boolean.TRUE);
    }

    public static void reset() {
        THREAD_LOCAL.remove();
    }

    public static boolean isEnabled() {
        return Objects.equals(THREAD_LOCAL.get(), Boolean.TRUE);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!isEnabled()) {
            return invocation.proceed();
        }

        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (mappedStatement.getSqlCommandType() != SqlCommandType.INSERT) {
            return invocation.proceed();
        }

        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        String sql = boundSql.getSql().replace("INSERT", "INSERT IGNORE").replace("insert", "INSERT IGNORE");
        metaObject.setValue("delegate.boundSql.sql", sql);
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }
}
