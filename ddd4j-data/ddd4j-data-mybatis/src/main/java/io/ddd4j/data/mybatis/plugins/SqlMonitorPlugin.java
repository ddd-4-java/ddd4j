package io.ddd4j.data.mybatis.plugins;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;

import java.sql.Connection;
import java.util.*;

/**
 * SQL 监控插件（零 MyBatis-Plus 依赖）。
 *
 * <p>MyBatis StatementHandler 拦截器，记录每条 SQL 的执行参数和耗时到 ThreadContext：
 * <ul>
 *   <li>{@code ContextConstants.PREPARING_SQL} — 当前执行的 SQL（压缩换行）</li>
 *   <li>{@code ContextConstants.SQL_PARAMS} — SQL 参数列表（排序后）</li>
 *   <li>{@code ContextConstants.LAST_SQL_SPENDS} — SQL 执行耗时（毫秒）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class SqlMonitorPlugin implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        ThreadContext.set(ContextConstants.PREPARING_SQL, boundSql.getSql().replaceAll("\\n", " ").replaceAll("\\s\\s", " "));

        if (statementHandler.getParameterHandler().getParameterObject() instanceof MapperMethod.ParamMap<?> parameterObject) {
            if (parameterObject.containsKey("param1") && parameterObject.get("param1") instanceof Map<?, ?> paramMap) {
                Map<String, Object> params = new HashMap<>();
                paramMap.forEach((k, v) -> {
                    if (v != null) params.put(String.valueOf(k), v);
                });
                if (!params.isEmpty()) {
                    List<String> keys = new ArrayList<>(params.keySet());
                    Collections.sort(keys);
                    List<String> sortedSqlParams = new ArrayList<>();
                    for (String key : keys) {
                        sortedSqlParams.add(String.valueOf(params.get(key)));
                    }
                    ThreadContext.set(ContextConstants.SQL_PARAMS, sortedSqlParams);
                }
            }
        }

        long startTime = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            ThreadContext.set(ContextConstants.LAST_SQL_SPENDS, (System.currentTimeMillis() - startTime));
        }
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }
}
