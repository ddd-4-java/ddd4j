package io.ddd4j.web.core.context;

import io.ddd4j.core.constant.ContextConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebContextScopeTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private static WebRequestContext request() {
        return new WebRequestContext("r-1", "t-1", "tenant-a", "Bearer token",
                Locale.CHINA, "127.0.0.1", "GET", "/api");
    }

    @Test
    void openAndCloseIsIdempotent() {
        WebContextScope scope = WebContextScope.open(request());
        scope.close();
        assertDoesNotThrow(scope::close);
    }

    @Test
    void openRejectsNullContext() {
        assertThrows(NullPointerException.class, () -> WebContextScope.open(null));
    }

    @Test
    void closeRestoresPreviousMdcContext() {
        MDC.put("existing", "before");
        WebContextScope scope = WebContextScope.open(request());
        MDC.put("existing", "during");
        scope.close();
        assertEquals("before", MDC.get("existing"));
    }

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
