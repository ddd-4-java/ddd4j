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
package io.ddd4j.web.core.context;

import io.ddd4j.core.constant.ContextConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>WebContextScope</code> 在<code>open</code>等操作中的行为契约。
 */
class WebContextScopeTest {

    /**
     * 创建 <code>new WebRequestContext</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "GET", "/api")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    /**
     * 测试结束后执行 <code>MDC.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /**
     * 在测试框架中执行 <code>WebContextScope.open()</code>、<code>request()</code>、<code>scope.close()</code>，验证该场景。
     */
    @Test
    void openAndCloseIsIdempotent() {
        WebContextScope scope = WebContextScope.open(request());
        scope.close();
        assertDoesNotThrow(scope::close);
    }

    /**
     * 验证<code>WebContextScope.open(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void openRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> WebContextScope.open(null));
    }

    /**
     * 验证<code>MDC.get("existing")</code> 等于 <code>"before"</code>。
     */
    @Test
    void closeRestoresPreviousMdcContext() {
        MDC.put("existing", "before");
        WebContextScope scope = WebContextScope.open(request());
        MDC.put("existing", "during");
        scope.close();
        assertEquals("before", MDC.get("existing"));
    }

    /**
     * 验证<code>MDC.get(ContextConstants.TENANT_ID)</code>为空，<code>MDC.get(ContextConstants.TENANT_ID)</code> 等于 <code>"stale"</code>。
     */
    @Test
    void openWithBlankValuesRemovesMdcKeys() {
        WebRequestContext blank = new WebRequestContext("r-1", null, null, null,
                null, null, null, null);
        MDC.put(ContextConstants.TENANT_ID, "stale");

        WebContextScope scope = WebContextScope.open(blank);
        assertNull(MDC.get(ContextConstants.TENANT_ID));
        scope.close();

        assertEquals("stale", MDC.get(ContextConstants.TENANT_ID));
    }
}
