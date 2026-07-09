package io.ddd4j.data.mybatis.plugins;

import io.ddd4j.core.api.Page;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分页插件（零 MyBatis-Plus 依赖）。
 *
 * <p>MyBatis StatementHandler 拦截器，拦截 SELECT 语句自动分页：
 * <ul>
 *   <li>从参数中查找 {@link Page} 或 {@link RowBounds} 对象</li>
 *   <li>将原始 SQL 改写为 {@code LIMIT offset, size}</li>
 *   <li>执行独立的 COUNT 查询填充 {@code page.total}</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * // 注册插件
 * PaginationPlugin plugin = new PaginationPlugin();
 * configuration.addInterceptor(plugin);
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class PaginationPlugin implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(PaginationPlugin.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler sh = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(sh);
        MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        // 仅拦截 SELECT 语句
        if (SqlCommandType.SELECT != ms.getSqlCommandType()
                || StatementType.CALLABLE == ms.getStatementType()) {
            return invocation.proceed();
        }

        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        Object paramObj = boundSql.getParameterObject();

        // 查找分页参数
        Page<?> page = findPageParameter(paramObj);
        if (page == null || page.getSize() <= 0) {
            return invocation.proceed();
        }

        String originalSql = boundSql.getSql().trim();

        // 改写为分页 SQL
        String paginationSql = buildPaginationSql(originalSql, page.getCurrent(), page.getSize());
        metaObject.setValue("delegate.boundSql.sql", paginationSql);

        if (log.isDebugEnabled()) {
            log.debug("Pagination applied: page={}, size={}, sql={}", page.getCurrent(), page.getSize(), paginationSql);
        }

        // 执行查询
        Object result = invocation.proceed();

        // 执行 COUNT 查询
        try {
            long total = executeCount(originalSql, ms, boundSql, (Connection) invocation.getArgs()[0]);
            page.setTotal(total);
        } catch (Exception e) {
            log.warn("Pagination count failed: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof StatementHandler ? Plugin.wrap(target, this) : target;
    }

    /**
     * 从参数对象中查找 Page 实例。
     */
    private Page<?> findPageParameter(Object parameterObject) {
        if (parameterObject == null) {
            return null;
        }
        if (parameterObject instanceof Page<?> page) {
            return page;
        }
        if (parameterObject instanceof Map<?, ?> parameterMap) {
            for (Object value : parameterMap.values()) {
                if (value instanceof Page<?> page) {
                    return page;
                }
            }
        }
        return null;
    }

    /**
     * 构建分页 SQL。
     */
    private String buildPaginationSql(String originalSql, long pageNum, long pageSize) {
        long offset = (pageNum - 1) * pageSize;
        return originalSql + " LIMIT " + offset + ", " + pageSize;
    }

    /**
     * 执行 COUNT 查询。
     */
    private long executeCount(String originalSql, MappedStatement ms, BoundSql boundSql, Connection connection) {
        String countSql = "SELECT COUNT(1) FROM (" + originalSql + ") TOTAL";
        try (PreparedStatement statement = connection.prepareStatement(countSql)) {
            // 复制参数
            List<org.apache.ibatis.mapping.ParameterMapping> mappings = boundSql.getParameterMappings();
            if (mappings != null && !mappings.isEmpty()) {
                Configuration configuration = ms.getConfiguration();
                Object parameterObject = boundSql.getParameterObject();
                MetaObject paramMeta = configuration.newMetaObject(parameterObject);
                for (int i = 0; i < mappings.size(); i++) {
                    org.apache.ibatis.mapping.ParameterMapping mapping = mappings.get(i);
                    if (mapping.getMode() != org.apache.ibatis.mapping.ParameterMode.OUT) {
                        Object value;
                        if (mapping.getExpression() != null && paramMeta.hasGetter(mapping.getProperty())) {
                            value = paramMeta.getValue(mapping.getProperty());
                        } else if (boundSql.hasAdditionalParameter(mapping.getProperty())) {
                            value = boundSql.getAdditionalParameter(mapping.getProperty());
                        } else {
                            value = null;
                        }
                        statement.setObject(i + 1, value);
                    }
                }
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            log.warn("Count query failed: {}", e.getMessage());
        }
        return 0L;
    }
}
