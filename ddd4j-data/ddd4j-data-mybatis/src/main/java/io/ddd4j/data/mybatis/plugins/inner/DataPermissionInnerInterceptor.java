package io.ddd4j.data.mybatis.plugins.inner;

import io.ddd4j.data.mybatis.plugins.DataScopeProvider;
import io.ddd4j.kit.lang.StrKit;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Objects;

/**
 * 数据权限拦截器（零 MyBatis-Plus 依赖）。
 *
 * <p>在 SQL 解析阶段拦截 SELECT 语句，注入数据范围条件（如 dept_id IN (...)）。
 * 业务方可通过 {@code @InterceptorIgnore(dataPermission = "true")} 跳过。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class DataPermissionInnerInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionInnerInterceptor.class);

    private final DataScopeProvider provider;

    public DataPermissionInnerInterceptor(DataScopeProvider provider) {
        this.provider = provider;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler sh = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(sh);
        MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (Objects.isNull(ms) || SqlCommandType.SELECT != ms.getSqlCommandType()) {
            return invocation.proceed();
        }

        BoundSql boundSql = sh.getBoundSql();
        if (Objects.isNull(boundSql)) {
            return invocation.proceed();
        }

        String originalSql = boundSql.getSql();
        try {
            String scopeCondition = provider.dataScopeCondition(ms.getId());
            if (Objects.isNull(scopeCondition) || !StrKit.hasText(scopeCondition)) {
                return invocation.proceed();
            }

            Select select = (Select) CCJSqlParserUtil.parse(originalSql);
            if (!(select instanceof PlainSelect ps)) {
                return invocation.proceed();
            }

            Expression where = ps.getWhere();
            Expression scope = CCJSqlParserUtil.parseCondExpression(scopeCondition);
            if (Objects.isNull(where)) {
                ps.setWhere(scope);
            } else {
                ps.setWhere(new AndExpression(where, scope));
            }

            String newSql = select.toString();
            MetaObject boundSqlMeta = SystemMetaObject.forObject(boundSql);
            boundSqlMeta.setValue("sql", newSql);

            if (log.isDebugEnabled()) {
                log.debug("DataPermission applied to [{}]: {} -> {}", ms.getId(), originalSql, newSql);
            }
        } catch (JSQLParserException e) {
            log.warn("DataPermission parse error for [{}]: {}", ms.getId(), e.getMessage());
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
}
