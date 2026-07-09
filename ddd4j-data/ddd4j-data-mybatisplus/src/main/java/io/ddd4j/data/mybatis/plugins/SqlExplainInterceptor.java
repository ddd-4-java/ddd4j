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
package io.ddd4j.data.mybatis.plugins;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Objects;

/**
 * P1-4: 慢 SQL 监控拦截器
 * <p>
 * 业务模块可注入 {@code SqlSlowLogger} 回调接口，自定义慢 SQL 处理（如上报 Micrometer、告警）。
 * </p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SqlExplainInterceptor implements InnerInterceptor {

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
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        MappedStatement ms = (MappedStatement) org.apache.ibatis.reflection.SystemMetaObject
                .forObject(sh)
                .getValue("delegate.mappedStatement");
        if (Objects.isNull(ms)) {
            return;
        }
        if (InterceptorIgnoreHelper.willIgnoreOthersByKey(ms.getId(), "slowSql")) {
            return;
        }
        BoundSql boundSql = sh.getBoundSql();
        String sql = Objects.nonNull(boundSql) ? boundSql.getSql() : null;
        if (Objects.nonNull(sql) && sql.length() > longSqlThreshold) {
            log.warn("Long SQL detected [length={}, mapper={}]: {}", sql.length(),
                    ms.getId(), sql.substring(0, 200) + "...");
        }
    }

    /**
     * 慢 SQL 记录回调
     */
    @FunctionalInterface
    public interface SqlSlowLogger {
        void onSlow(String mappedStatementId, String sql, long elapsedMs);
    }
}
