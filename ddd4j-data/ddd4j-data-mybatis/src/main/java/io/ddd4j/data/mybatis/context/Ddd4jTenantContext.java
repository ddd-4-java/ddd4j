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
package io.ddd4j.data.mybatis.context;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import org.apache.ibatis.enhance.context.TenantContext;

import java.util.Objects;

/**
 * 将 ddd4j {@link ThreadContext} 中的租户上下文适配为 mybatis-plus-enhance 的租户上下文。
 *
 * <p>该适配器不维护第二份线程变量，ddd4j 的请求上下文始终是租户 ID 的唯一事实来源。</p>
 *
 * <pre>{@code
 * TenantLineHandler handler = new DefaultTenantLineHandler(new Ddd4jTenantContext());
 * TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(handler);
 * }</pre>
 */
public class Ddd4jTenantContext extends TenantContext {

    @Override
    public Object getCurrentTenantId() {
        return ThreadContext.get(ContextConstants.TENANT_ID);
    }

    @Override
    public void setCurrentTenantId(Object tenantId) {
        if (Objects.isNull(tenantId)) {
            clear();
            return;
        }
        ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
    }

    @Override
    public void clear() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
    }
}
