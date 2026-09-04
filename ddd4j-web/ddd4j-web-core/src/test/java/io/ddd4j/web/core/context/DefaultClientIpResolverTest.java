package io.ddd4j.web.core.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultClientIpResolverTest {

    @Test
    void resolvesRemoteAddressWhenNotTrustingForwardedHeaders() {
        DefaultClientIpResolver resolver = new DefaultClientIpResolver(false);
        assertEquals("10.0.0.1", resolver.resolve("1.2.3.4", "5.6.7.8", "10.0.0.1"));
        assertNull(resolver.resolve("1.2.3.4", "5.6.7.8", null));
        assertNull(resolver.resolve(null, null, "  "));
    }

    @Test
    void prefersForwardedAddressWhenTrustingHeaders() {
        DefaultClientIpResolver resolver = new DefaultClientIpResolver(true);
        assertEquals("1.2.3.4", resolver.resolve("1.2.3.4, 9.9.9.9", "5.6.7.8", "10.0.0.1"));
        assertEquals("5.6.7.8", resolver.resolve("", " 5.6.7.8 ", "10.0.0.1"));
        assertEquals("10.0.0.1", resolver.resolve(null, null, "10.0.0.1"));
    }

    @Test
    void trimsForwardedAddresses() {
        DefaultClientIpResolver resolver = new DefaultClientIpResolver(true);
        assertEquals("1.2.3.4", resolver.resolve(" 1.2.3.4 ", null, null));
    }
}
