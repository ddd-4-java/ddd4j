package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** \u901a\u8fc7 <code>ThreadContext.set()</code>\u3001<code>context.getCurrentTenantId()</code>\u3001<code>context.setCurrentTenantId()</code> \u9a8c\u8bc1 <code>Ddd4jTenantContext</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class Ddd4jTenantContextTest {

    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u6846\u67b6\u4e0a\u4e0b\u6587\u6216\u8bf7\u6c42\u4f5c\u7528\u57df\u3002 */
    private final Ddd4jTenantContext context = new Ddd4jTenantContext();

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void tearDown() {
        ThreadContext.clear();
    }

    /** \u9a8c\u8bc1<code>context.getCurrentTenantId()</code> \u7b49\u4e8e <code>"tenant-a"</code>\u3002 */
    @Test
    void shouldUseDdd4jThreadContextAsTenantSource() {
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-a");

        assertEquals("tenant-a", context.getCurrentTenantId());
    }

    /** \u9a8c\u8bc1<code>ThreadContext.get(ContextConstants.TENANT_ID)</code> \u7b49\u4e8e <code>Long.valueOf(1001L)</code>\uff0c<code>ThreadContext.get(ContextConstants.TENANT_ID)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void shouldWriteAndClearDdd4jThreadContext() {
        context.setCurrentTenantId(1001L);
        assertEquals(Long.valueOf(1001L), ThreadContext.get(ContextConstants.TENANT_ID));

        context.clear();
        assertNull(ThreadContext.get(ContextConstants.TENANT_ID));
    }

    /** \u9a8c\u8bc1<code>context.getCurrentTenantId()</code> \u7b49\u4e8e <code>"tenant-b"</code>\uff0c<code>context.getCurrentTenantId()</code> \u7b49\u4e8e <code>"tenant-a"</code>\u3002 */
    @Test
    void shouldRestoreDdd4jTenantAfterNestedScope() {
        context.setCurrentTenantId("tenant-a");

        try (Ddd4jTenantContext.Scope ignored = context.open("tenant-b")) {
            assertEquals("tenant-b", context.getCurrentTenantId());
        }

        assertEquals("tenant-a", context.getCurrentTenantId());
    }
}
