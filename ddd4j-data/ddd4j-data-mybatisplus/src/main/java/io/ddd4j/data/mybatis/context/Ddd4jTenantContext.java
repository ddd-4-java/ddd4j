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

    /**
     * \u8bbe\u7f6e\u5f53\u524d\u8bf7\u6c42\u7684\u79df\u6237\u3002
     * @param tenantId \u79df\u6237\u6807\u8bc6\uff0c\u4f20\u5165 null \u65f6\u6e05\u9664\u5f53\u524d\u79df\u6237
     */
    public void setCurrentTenantId(Object tenantId) {
        if (Objects.isNull(tenantId)) {
            clear();
            return;
        }
        ThreadContext.set(ContextConstants.TENANT_ID, tenantId);
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u7684\u79df\u6237\u3002
     * @return \u5f53\u524d\u79df\u6237\u6807\u8bc6\uff0c\u65e0\u79df\u6237\u65f6\u8fd4\u56de null
     */
    public Object getCurrentTenantId() {
        return ThreadContext.get(ContextConstants.TENANT_ID);
    }

    /** \u4ece\u5f53\u524d\u8bf7\u6c42\u4e0a\u4e0b\u6587\u6e05\u9664\u79df\u6237\u3002 */
    public void clear() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
    }

    /**
     * \u5728\u4e34\u65f6\u4f5c\u7528\u57df\u5185\u8bbe\u7f6e\u79df\u6237\uff0c\u7531\u521b\u5efa\u7ebf\u7a0b\u5173\u95ed\u4f5c\u7528\u57df\u65f6\u6062\u590d\u539f\u503c\u3002
     * @param tenantId \u4e34\u65f6\u4f5c\u7528\u57df\u7684\u79df\u6237\u6807\u8bc6
     * @return \u53ef\u5173\u95ed\u7684\u79df\u6237\u4f5c\u7528\u57df
     */
    public Scope open(Object tenantId) {
        Object previous = getCurrentTenantId();
        setCurrentTenantId(tenantId);
        return new Scope(previous);
    }

    /** \u590d\u7528\u589e\u5f3a\u7ec4\u4ef6\u7684\u521b\u5efa\u7ebf\u7a0b\u6821\u9a8c\u3001\u79df\u6237\u6062\u590d\u4e0e\u5e42\u7b49\u5173\u95ed\u903b\u8f91\u3002 */
    public final class Scope implements AutoCloseable {

        /** 保存 <code>previous</code> 对应的 <code>Object</code> 数据。 */
        private final Object previous;

        /**
         * 初始化 <code>Scope</code> 使用的 <code>previous</code>。
         *
         * @param previous 传入此操作的 <code>Object</code> 类型 <code>previous</code>
         */
        private Scope(Object previous) {
            this.previous = previous;
        }

        /**
         * \u5728\u521b\u5efa\u7ebf\u7a0b\u6062\u590d\u539f\u79df\u6237\uff1b\u540c\u7ebf\u7a0b\u91cd\u590d\u5173\u95ed\u4e0d\u518d\u6539\u53d8\u4e0a\u4e0b\u6587\u3002
         * @throws IllegalStateException \u7531\u5176\u4ed6\u7ebf\u7a0b\u5173\u95ed\uff0c\u5305\u62ec\u5df2\u7ecf\u5173\u95ed\u7684\u4f5c\u7528\u57df
         */
        @Override
        public void close() {
            setCurrentTenantId(previous);
        }
    }
}
