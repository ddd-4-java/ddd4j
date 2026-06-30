package io.ddd4j.data.mybatis.plugin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SQL 监控插件。
 *
 * <p>MyBatis StatementHandler 拦截器，记录每条 SQL 的执行参数和耗时到 ThreadContext：
 * <ul>
 *   <li>{@code ContextConstants.PREPARING_SQL} — 当前执行的 SQL（压缩换行）</li>
 *   <li>{@code ContextConstants.SQL_PARAMS} — SQL 参数列表（排序后）</li>
 *   <li>{@code ContextConstants.LAST_SQL_SPENDS} — SQL 执行耗时（毫秒）</li>
 * </ul>
 *
 * <p>配合 {@code BaseRepositoryImpl.getSqlLine()} 可在日志中打印完整的 SQL 执行信息。
 *
 * <p>使用方式：
 * <pre>
 * &#64;Bean
 * public SqlMonitorPlugin sqlMonitorPlugin() {
 *     return new SqlMonitorPlugin();
 * }
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class SqlMonitorPlugin implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        ThreadContext.set(ContextConstants.PREPARING_SQL, boundSql.getSql().replaceAll("\\n", " ").replaceAll("\\s\\s", " "));
        if (statementHandler.getParameterHandler().getParameterObject() instanceof MapperMethod.ParamMap) {
            MapperMethod.ParamMap parameterObject = (MapperMethod.ParamMap) statementHandler.getParameterHandler().getParameterObject();
            if (parameterObject.containsKey("param1") && parameterObject.get("param1") instanceof QueryWrapper) {
                Map<String, Object> params = ((QueryWrapper) parameterObject.get("param1")).getParamNameValuePairs();
                if (java.util.Objects.nonNull(params) && params.size() > 0) {
                    List<String> keys = new ArrayList<>(params.keySet());
                    Collections.sort(keys);
                    List<String> sortedSqlParams = new ArrayList<>();
                    for (String key : keys) {
                        Object o = params.get(key);
                        if (java.util.Objects.nonNull(o)) {
                            sortedSqlParams.add(String.valueOf(o));
                        }
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
