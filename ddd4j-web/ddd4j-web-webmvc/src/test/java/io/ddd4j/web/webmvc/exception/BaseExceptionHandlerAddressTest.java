package io.ddd4j.web.webmvc.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseExceptionHandlerAddressTest {

    @Test
    void forwardedAddressTakesPrecedenceInExceptionLog() {
        AddressRequest request = new AddressRequest("203.0.113.10, 10.0.0.7", "198.51.100.20", "10.0.0.8");

        assertEquals("203.0.113.10", BaseExceptionHandler.clientAddress(request));
    }

    @Test
    void remoteAddressIsUsedWithoutProxyHeaders() {
        AddressRequest request = new AddressRequest(null, null, "10.0.0.8");

        assertEquals("10.0.0.8", BaseExceptionHandler.clientAddress(request));
        assertEquals("unknown", BaseExceptionHandler.clientAddress(null));
    }

    public static final class AddressRequest {
        private final String forwardedFor;
        private final String realIp;
        private final String remoteAddress;

        AddressRequest(String forwardedFor, String realIp, String remoteAddress) {
            this.forwardedFor = forwardedFor;
            this.realIp = realIp;
            this.remoteAddress = remoteAddress;
        }

        public String getHeader(String name) {
            if ("X-Forwarded-For".equals(name)) {
                return forwardedFor;
            }
            if ("X-Real-IP".equals(name)) {
                return realIp;
            }
            return null;
        }

        public String getRemoteAddr() {
            return remoteAddress;
        }
    }
}
