package io.ddd4j.web.core.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u9a8c\u8bc1 <code>ReadinessEndpoint</code> \u5728<code>constructor</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class ReadinessEndpointTest {

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.ready()</code>\u4e3a\u771f\uff0c<code>response.httpStatus()</code> \u7b49\u4e8e <code>200</code>\u3002 */
    @Test
    void readinessReportsReady() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> true);
        ReadinessResponse response = endpoint.readiness();
        assertNotNull(response);
        assertTrue(response.ready());
        assertEquals(200, response.httpStatus());
    }

    /** \u9a8c\u8bc1<code>response.ready()</code>\u4e3a\u5047\uff0c<code>response.httpStatus()</code> \u7b49\u4e8e <code>503</code>\u3002 */
    @Test
    void readinessReportsUnready() {
        ReadinessEndpoint endpoint = new ReadinessEndpoint(() -> false);
        ReadinessResponse response = endpoint.readiness();
        assertFalse(response.ready());
        assertEquals(503, response.httpStatus());
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullSupplier() {
        assertThrows(NullPointerException.class, () -> new ReadinessEndpoint(null));
    }

    /** \u9a8c\u8bc1<code>ReadinessEndpoint.PATH</code> \u7b49\u4e8e <code>"/-/ready"</code>\u3002 */
    @Test
    void exposesProbePath() {
        assertEquals("/-/ready", ReadinessEndpoint.PATH);
    }
}
