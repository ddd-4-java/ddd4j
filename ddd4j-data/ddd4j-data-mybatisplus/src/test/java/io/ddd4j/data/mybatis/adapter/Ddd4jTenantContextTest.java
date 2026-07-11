package io.ddd4j.data.mybatis.adapter;

import com.baomidou.mybatisplus.enhance.tenant.TenantContext;
import com.baomidou.mybatisplus.enhance.tenant.DefaultTenantLineHandler;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Ddd4jTenantContextTest {

	private final Ddd4jTenantContext context = new Ddd4jTenantContext();

	@AfterEach
	void tearDown() {
		ThreadContext.clear();
	}

	@Test
	void shouldUseDdd4jThreadContextAsTenantSource() {
		ThreadContext.set(ContextConstants.TENANT_ID, "tenant-a");

		assertEquals("tenant-a", context.getCurrentTenantId());
		assertEquals(new StringValue("tenant-a"), new DefaultTenantLineHandler(context).getTenantId());
	}

	@Test
	void shouldWriteAndClearDdd4jThreadContext() {
		context.setCurrentTenantId(1001L);
		assertEquals(Long.valueOf(1001L), ThreadContext.get(ContextConstants.TENANT_ID));

		context.clear();
		assertNull(ThreadContext.get(ContextConstants.TENANT_ID));
	}

	@Test
	void shouldRestoreDdd4jTenantAfterNestedScope() {
		context.setCurrentTenantId("tenant-a");

		try (TenantContext.Scope ignored = context.open("tenant-b")) {
			assertEquals("tenant-b", context.getCurrentTenantId());
		}

		assertEquals("tenant-a", context.getCurrentTenantId());
	}
}
