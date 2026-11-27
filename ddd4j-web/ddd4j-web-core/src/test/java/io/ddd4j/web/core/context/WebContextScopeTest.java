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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.ddd4j.core.constant.ContextConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class WebContextScopeTest {

    /**
     * \u521b\u5efa <code>new WebRequestContext</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token", Locale.CHINA, "127.0.0.1", "GET", "/api")</code>
     */
    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>MDC.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>WebContextScope.open()</code>\u3001<code>request()</code>\u3001<code>scope.close()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void openAndCloseIsIdempotent() {
        WebContextScope scope = WebContextScope.open(request());
        scope.close();
        assertDoesNotThrow(scope::close);
    }

    /** \u9a8c\u8bc1<code>WebContextScope.open(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void openRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> WebContextScope.open(null));
    }

    /** \u9a8c\u8bc1<code>MDC.get("existing")</code> \u7b49\u4e8e <code>"before"</code>\u3002 */
    @Test
    void closeRestoresPreviousMdcContext() {
        MDC.put("existing", "before");
        WebContextScope scope = WebContextScope.open(request());
        MDC.put("existing", "during");
        scope.close();
        assertEquals("before", MDC.get("existing"));
    }

    /** \u9a8c\u8bc1<code>MDC.get(ContextConstants.TENANT_ID)</code>\u4e3a\u7a7a\uff0c<code>MDC.get(ContextConstants.TENANT_ID)</code> \u7b49\u4e8e <code>"stale"</code>\u3002 */
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
