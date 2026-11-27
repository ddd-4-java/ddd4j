package io.ddd4j.web.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** \u9a8c\u8bc1 <code>DefaultClientIpResolver</code> \u5728<code>resolvesRemoteAddress</code>\u3001<code>prefersForwardedAddress</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class DefaultClientIpResolverTest {

    /** \u9a8c\u8bc1<code>resolver.resolve("1.2.3.4", "5.6.7.8", null)</code>\u4e3a\u7a7a\uff0c<code>resolver.resolve(null, null, " ")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void resolvesRemoteAddressWhenNotTrustingForwardedHeaders() {
        DefaultClientIpResolver resolver = new DefaultClientIpResolver(false);
        assertEquals("10.0.0.1", resolver.resolve("1.2.3.4", "5.6.7.8", "10.0.0.1"));
        assertNull(resolver.resolve("1.2.3.4", "5.6.7.8", null));
        assertNull(resolver.resolve(null, null, "  "));
    }

    /** \u9a8c\u8bc1<code>resolver.resolve(null, null, "10.0.0.1")</code> \u7b49\u4e8e <code>"10.0.0.1"</code>\u3002 */
    @Test
    void prefersForwardedAddressWhenTrustingHeaders() {
        DefaultClientIpResolver resolver = new DefaultClientIpResolver(true);
        assertEquals("1.2.3.4", resolver.resolve("1.2.3.4, 9.9.9.9", "5.6.7.8", "10.0.0.1"));
        assertEquals("5.6.7.8", resolver.resolve("", " 5.6.7.8 ", "10.0.0.1"));
        assertEquals("10.0.0.1", resolver.resolve(null, null, "10.0.0.1"));
    }

    /** \u9a8c\u8bc1<code>resolver.resolve(" 1.2.3.4 ", null, null)</code> \u7b49\u4e8e <code>"1.2.3.4"</code>\u3002 */
    @Test
    void trimsForwardedAddresses() {
        DefaultClientIpResolver resolver = new DefaultClientIpResolver(true);
        assertEquals("1.2.3.4", resolver.resolve(" 1.2.3.4 ", null, null));
    }
}
