package io.ddd4j.data.mybatis.plugins.inner;

import io.ddd4j.data.mybatis.plugins.handler.DefaultTenantLineHandler;
import io.ddd4j.data.mybatis.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
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
 * 多租户拦截器（零 MyBatis-Plus 依赖，纯 JSqlParser 实现）。
 *
 * <p>所有 SQL 自动追加 {@code WHERE tenant_id = ?}，通过 {@link TenantLineHandler} 提供租户 ID。
 * 业务方可通过 {@code @InterceptorIgnore(tenantLine = "true")} 跳过。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class DefaultTenantLineInnerInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantLineInnerInterceptor.class);

    private final TenantLineHandler tenantHandler;

    public DefaultTenantLineInnerInterceptor() {
        this(new DefaultTenantLineHandler());
    }

    public DefaultTenantLineInnerInterceptor(TenantLineHandler tenantHandler) {
        this.tenantHandler = tenantHandler;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler sh = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(sh);
        MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (Objects.isNull(ms)) {
            return invocation.proceed();
        }

        SqlCommandType type = ms.getSqlCommandType();
        if (type != SqlCommandType.SELECT && type != SqlCommandType.UPDATE && type != SqlCommandType.DELETE) {
            return invocation.proceed();
        }

        BoundSql boundSql = sh.getBoundSql();
        String originalSql = boundSql.getSql();
        if (originalSql == null || originalSql.isEmpty()) {
            return invocation.proceed();
        }

        try {
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            String newSql = null;

            if (stmt instanceof Select select) {
                if (select instanceof PlainSelect ps) {
                    if (ps.getFromItem() instanceof Table table && !tenantHandler.ignoreTable(table.getName())) {
                        Expression where = ps.getWhere();
                        Expression tenantCondition = buildTenantCondition(table, tenantHandler.getTenantIdColumn(), tenantHandler.getTenantId());
                        if (where == null) {
                            ps.setWhere(tenantCondition);
                        } else {
                            ps.setWhere(new AndExpression(where, tenantCondition));
                        }
                        newSql = select.toString();
                    }
                }
            } else if (stmt instanceof Update update) {
                if (update.getTable() != null && !tenantHandler.ignoreTable(update.getTable().getName())) {
                    Expression where = update.getWhere();
                    Expression tenantCondition = buildTenantCondition(update.getTable(), tenantHandler.getTenantIdColumn(), tenantHandler.getTenantId());
                    if (where == null) {
                        update.setWhere(tenantCondition);
                    } else {
                        update.setWhere(new AndExpression(where, tenantCondition));
                    }
                    newSql = update.toString();
                }
            } else if (stmt instanceof Delete delete) {
                if (delete.getTable() != null && !tenantHandler.ignoreTable(delete.getTable().getName())) {
                    Expression where = delete.getWhere();
                    Expression tenantCondition = buildTenantCondition(delete.getTable(), tenantHandler.getTenantIdColumn(), tenantHandler.getTenantId());
                    if (where == null) {
                        delete.setWhere(tenantCondition);
                    } else {
                        delete.setWhere(new AndExpression(where, tenantCondition));
                    }
                    newSql = delete.toString();
                }
            }

            if (newSql != null) {
                MetaObject boundSqlMeta = SystemMetaObject.forObject(boundSql);
                boundSqlMeta.setValue("sql", newSql);
                if (log.isDebugEnabled()) {
                    log.debug("TenantLine applied to [{}]: {} -> {}", ms.getId(), originalSql, newSql);
                }
            }
        } catch (JSQLParserException e) {
            log.warn("TenantLine parse error for [{}]: {}", ms.getId(), e.getMessage());
        }

        return invocation.proceed();
    }

    private Expression buildTenantCondition(Table table, String tenantColumn, Object tenantId) {
        Column column = new Column(table.getName() + "." + tenantColumn);
        if (tenantId instanceof String str) {
            return new EqualsTo(column, new net.sf.jsqlparser.expression.StringValue(str));
        }
        return new EqualsTo(column, new net.sf.jsqlparser.expression.LongValue(tenantId.toString()));
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }
}
