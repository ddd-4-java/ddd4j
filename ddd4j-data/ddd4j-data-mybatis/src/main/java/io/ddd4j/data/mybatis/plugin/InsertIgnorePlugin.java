package io.ddd4j.data.mybatis.plugin;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;

import java.sql.Connection;
import java.util.Objects;

/**
 * INSERT IGNORE 改写插件。
 *
 * <p>MyBatis StatementHandler 拦截器，将 INSERT 语句改写为 INSERT IGNORE。
 * 通过 {@link ThreadLocal} 开关控制，仅在实际需要时启用。
 *
 * <p>典型场景：配合唯一索引实现幂等插入——重复插入时静默忽略而不抛异常。
 *
 * <p>使用方式：
 * <pre>
 * // 1. 注册插件
 * &#64;Bean
 * public InsertIgnorePlugin insertIgnorePlugin() {
 *     return new InsertIgnorePlugin();
 * }
 *
 * // 2. 在需要 INSERT IGNORE 的地方启用开关
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

    /**
     * 开启 INSERT IGNORE 改写（当前线程生效）。
     * <p>必须在调用后于 finally 中调用 {@link #reset()} 重置。
     */
    public static void enable() {
        THREAD_LOCAL.set(Boolean.TRUE);
    }

    /**
     * 重置 INSERT IGNORE 开关（必须在 finally 中调用）。
     */
    public static void reset() {
        THREAD_LOCAL.remove();
    }

    /**
     * 查询当前线程是否启用了 INSERT IGNORE。
     */
    public static boolean isEnabled() {
        return Objects.equals(THREAD_LOCAL.get(), Boolean.TRUE);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!isEnabled()) {
            return invocation.proceed();
        }

        StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
        org.apache.ibatis.reflection.MetaObject metaObject = org.apache.ibatis.reflection.SystemMetaObject.forObject(statementHandler);
        // 只针对 INSERT 操作
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (mappedStatement.getSqlCommandType() != SqlCommandType.INSERT) {
            return invocation.proceed();
        }

        // 改写 SQL：INSERT → INSERT IGNORE
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
