/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 <code>Ddd4jTenantRequestContextContract</code> 在 <code>sharesTenantAndRestoresNestedScopes</code>、<code>removesTemporaryTenant</code> 等场景中的行为。
 */
class Ddd4jTenantRequestContextContractTest {

    /**
     * 用于检查当前请求上下文是否保留或已清除的标记。
     */
    private static final String REQUEST_MARKER = Ddd4jTenantRequestContextContractTest.class.getName();

    /**
     * 测试后依次移除<code>ThreadContext</code>。
     */
    @AfterEach
    void restoreRequestContext() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
        ThreadContext.remove(REQUEST_MARKER);
    }

    /**
     * 验证<code>first.getCurrentTenantId()</code> 等于 <code>"request-tenant"</code>，<code>second.getCurrentTenantId()</code> 等于 <code>"request-tenant"</code>，<code>first.getCurrentTenantId()</code> 等于 <code>"outer-tenant"</code>。
     */
    @Test
    void sharesTenantAndRestoresNestedScopesWithoutClearingRequestData() {
        Ddd4jTenantContext first = new Ddd4jTenantContext();
        Ddd4jTenantContext second = new Ddd4jTenantContext();
        ThreadContext.set(ContextConstants.TENANT_ID, "request-tenant");
        ThreadContext.set(REQUEST_MARKER, "existing-request");

        assertEquals("request-tenant", first.getCurrentTenantId());
        assertEquals("request-tenant", second.getCurrentTenantId());

        try (Ddd4jTenantContext.Scope outer = first.open("outer-tenant")) {
            assertEquals("outer-tenant", ThreadContext.get(ContextConstants.TENANT_ID));
            assertEquals("outer-tenant", second.getCurrentTenantId());
            try (Ddd4jTenantContext.Scope inner = second.open(null)) {
                assertNull(first.getCurrentTenantId());
                assertNull(ThreadContext.get(ContextConstants.TENANT_ID));
                assertEquals("existing-request", ThreadContext.get(REQUEST_MARKER));
            }
            assertEquals("outer-tenant", first.getCurrentTenantId());
        }

        assertEquals("request-tenant", second.getCurrentTenantId());
        assertEquals("existing-request", ThreadContext.get(REQUEST_MARKER));
    }

    /**
     * 验证<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> 等于 <code>"temporary-tenant"</code>，<code>ThreadContext.get(ContextConstants.TENANT_ID)</code>为空。
     */
    @Test
    void removesTemporaryTenantWhenRequestStartedWithoutOne() {
        Ddd4jTenantContext context = new Ddd4jTenantContext();
        context.clear();
        try (Ddd4jTenantContext.Scope ignored = context.open("temporary-tenant")) {
            assertEquals("temporary-tenant", ThreadContext.get(ContextConstants.TENANT_ID));
        }
        assertNull(ThreadContext.get(ContextConstants.TENANT_ID));
    }
}
