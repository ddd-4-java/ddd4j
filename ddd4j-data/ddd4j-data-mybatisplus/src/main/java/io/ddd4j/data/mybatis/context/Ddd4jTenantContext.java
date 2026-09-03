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

import java.util.Objects;

/**
 * MyBatis-Plus 访问 ddd4j {@link ThreadContext} 租户上下文的适配器。
 *
 * <p>该适配器不维护第二份线程变量，ddd4j 的请求上下文始终是租户 ID 的唯一事实来源。</p>
 *
 */
public class Ddd4jTenantContext {

    public void setCurrentTenantId(Object tenantId) {
		if (Objects.isNull(tenantId)) {
			clear();
			return;
		}
		ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
	}

    public Object getCurrentTenantId() {
		return ThreadContext.get(ContextConstants.TENANT_ID);
	}

    public void clear() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
    }

    public Scope open(Object tenantId) {
        Object previous = getCurrentTenantId();
        setCurrentTenantId(tenantId);
        return new Scope(previous);
    }

    public final class Scope implements AutoCloseable {

        private final Object previous;

        private Scope(Object previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            setCurrentTenantId(previous);
        }
    }
}
