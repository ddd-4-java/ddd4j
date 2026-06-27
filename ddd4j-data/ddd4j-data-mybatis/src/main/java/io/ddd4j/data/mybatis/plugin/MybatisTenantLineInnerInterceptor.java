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

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;

/**
 * P1-4: 多租户拦截器
 * <p>
 * 所有 SQL 自动追加 {@code WHERE tenant_id = ?}，通过 {@link TenantLineHandler} 提供租户 ID。
 * 业务方可通过 {@code @InterceptorIgnore(tenantLine = "true")} 跳过。
 * </p>
 *
 * @author hiwepy
 */
public class MybatisTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    public MybatisTenantLineInnerInterceptor(TenantLineHandler tenantLineHandler) {
        super(tenantLineHandler);
    }

    /**
     * 默认租户处理器：读取系统属性 {@code ddd4j.tenant.id} 作为租户 ID
     */
    public static class DefaultTenantLineHandler implements TenantLineHandler {

        public static final String DEFAULT_TENANT_ID_KEY = "ddd4j.tenant.id";
        public static final String DEFAULT_TENANT_COLUMN = "tenant_id";

        @Override
        public Expression getTenantId() {
            String tenantId = System.getProperty(DEFAULT_TENANT_ID_KEY, "0");
            try {
                return new StringValue(tenantId);
            } catch (Exception e) {
                return new NullValue();
            }
        }

        @Override
        public String getTenantIdColumn() {
            return DEFAULT_TENANT_COLUMN;
        }

        @Override
        public boolean ignoreTable(String tableName) {
            // 系统表跳过
            return tableName.startsWith("qrtz_") || tableName.startsWith("act_") || tableName.startsWith("flw_");
        }
    }
}
