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

/** \u9a8c\u8bc1 <code>Ddd4jTenantRequestContextContract</code> \u5728 <code>sharesTenantAndRestoresNestedScopes</code>\u3001<code>removesTemporaryTenant</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class Ddd4jTenantRequestContextContractTest {

    /** \u7528\u4e8e\u68c0\u67e5\u5f53\u524d\u8bf7\u6c42\u4e0a\u4e0b\u6587\u662f\u5426\u4fdd\u7559\u6216\u5df2\u6e05\u9664\u7684\u6807\u8bb0\u3002 */
    private static final String REQUEST_MARKER = Ddd4jTenantRequestContextContractTest.class.getName();

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u79fb\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void restoreRequestContext() {
        ThreadContext.remove(ContextConstants.TENANT_ID);
        ThreadContext.remove(REQUEST_MARKER);
    }

    /** \u9a8c\u8bc1<code>first.getCurrentTenantId()</code> \u7b49\u4e8e <code>"request-tenant"</code>\uff0c<code>second.getCurrentTenantId()</code> \u7b49\u4e8e <code>"request-tenant"</code>\uff0c<code>first.getCurrentTenantId()</code> \u7b49\u4e8e <code>"outer-tenant"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> \u7b49\u4e8e <code>"temporary-tenant"</code>\uff0c<code>ThreadContext.get(ContextConstants.TENANT_ID)</code>\u4e3a\u7a7a\u3002 */
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
