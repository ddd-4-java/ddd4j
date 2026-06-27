/*
 * Copyright 2017-2026 the original author hiwepy.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.data.mybatis.plugin;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
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
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * P1-4: 数据权限拦截器（对接 ddd4j-auth-datascope）
 * <p>
 * 在 SQL 解析阶段拦截 SELECT 语句，注入数据范围条件（如 dept_id IN (...)）。
 * 业务方可通过 {@code @InterceptorIgnore(dataPermission = "true")} 跳过。
 * </p>
 *
 * @author hiwepy
 */
public class DataPermissionInnerInterceptor implements InnerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(DataPermissionInnerInterceptor.class);

    private final DataScopeProvider provider;

    public DataPermissionInnerInterceptor(DataScopeProvider provider) {
        this.provider = provider;
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        MappedStatement ms = (MappedStatement) SystemMetaObject.forObject(sh)
                .getValue("delegate.mappedStatement");
        if (ms == null || SqlCommandType.SELECT != ms.getSqlCommandType()) {
            return;
        }
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return;
        }
        BoundSql boundSql = sh.getBoundSql();
        if (boundSql == null) {
            return;
        }
        String originalSql = boundSql.getSql();
        try {
            String scopeCondition = provider.dataScopeCondition(ms.getId());
            if (scopeCondition == null || scopeCondition.isEmpty()) {
                return;
            }
            Select select = (Select) CCJSqlParserUtil.parse(originalSql);
            Object body = select.getSelectBody();
            if (!(body instanceof PlainSelect)) {
                return;
            }
            PlainSelect ps = (PlainSelect) body;
            Expression where = ps.getWhere();
            Expression scope = CCJSqlParserUtil.parseCondExpression(scopeCondition);
            if (where == null) {
                ps.setWhere(scope);
            } else {
                ps.setWhere(new AndExpression(where, scope));
            }
            String newSql = select.toString();
            MetaObject metaObject = SystemMetaObject.forObject(boundSql);
            metaObject.setValue("sql", newSql);
            if (LOG.isDebugEnabled()) {
                LOG.debug("DataPermission applied to [{}]: {} -> {}", ms.getId(), originalSql, newSql);
            }
        } catch (JSQLParserException e) {
            LOG.warn("DataPermission parse error for [{}]: {}", ms.getId(), e.getMessage());
        }
    }
}
