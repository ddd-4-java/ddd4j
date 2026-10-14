/*
 * Copyright 2017-2026 the original author redacted-legacy-family.
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

    /**
     * 写入当前线程的租户 ID。
     *
     * @param tenantId 租户 ID；为 {@code null} 时等价于 {@link #clear()}
     */
    public void setCurrentTenantId(Object tenantId) {
        if (Objects.isNull(tenantId)) {
            clear();
            return;
        }
        ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
    }

    /**
     * 读取当前线程绑定的租户 ID。
     *
     * @return 租户 ID，未设置时返回 {@code null}
     */
    public Object getCurrentTenantId() {
        return ThreadContext.get(ContextConstants.TENANT_ID);
    }

    /**
     * 清除当前线程绑定的租户 ID。
     */
    public void clear() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
    }

    /**
     * 切换到指定租户，并返回用于恢复原租户的作用域。
     *
     * @param tenantId 目标租户 ID
     * @return {@link Scope}：关闭（close）时恢复进入前的租户 ID
     */
    public Scope open(Object tenantId) {
        Object previous = getCurrentTenantId();
        setCurrentTenantId(tenantId);
        return new Scope(previous);
    }

    /**
     * 租户切换作用域，配合 try-with-resources 使用。
     *
     * <p>close 时将租户 ID 恢复为进入前的取值，保证线程上下文不外泄。
     */
    public final class Scope implements AutoCloseable {

        private final Object previous;

        private Scope(Object previous) {
            this.previous = previous;
        }

        /**
         * 恢复进入作用域前的租户 ID。
         */
        @Override
        public void close() {
            setCurrentTenantId(previous);
        }
    }
}
