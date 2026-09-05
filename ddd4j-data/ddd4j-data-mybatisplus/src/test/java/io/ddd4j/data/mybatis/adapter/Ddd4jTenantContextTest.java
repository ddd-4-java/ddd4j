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
package io.ddd4j.data.mybatis.adapter;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
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

        try (Ddd4jTenantContext.Scope ignored = context.open("tenant-b")) {
			assertEquals("tenant-b", context.getCurrentTenantId());
		}

		assertEquals("tenant-a", context.getCurrentTenantId());
	}
}
