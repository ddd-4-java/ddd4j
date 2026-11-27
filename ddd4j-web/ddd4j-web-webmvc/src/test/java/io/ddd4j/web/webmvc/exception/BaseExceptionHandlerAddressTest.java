package io.ddd4j.web.webmvc.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** \u9a8c\u8bc1 <code>BaseExceptionHandlerAddress</code> \u5728 <code>remoteAddressIsUsed</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class BaseExceptionHandlerAddressTest {

    /** \u9a8c\u8bc1<code>BaseExceptionHandler.clientAddress(request)</code> \u7b49\u4e8e <code>"203.0.113.10"</code>\u3002 */
    @Test
    void forwardedAddressTakesPrecedenceInExceptionLog() {
        AddressRequest request = new AddressRequest("203.0.113.10, 10.0.0.7", "198.51.100.20", "10.0.0.8");

        assertEquals("203.0.113.10", BaseExceptionHandler.clientAddress(request));
    }

    /** \u9a8c\u8bc1<code>BaseExceptionHandler.clientAddress(request)</code> \u7b49\u4e8e <code>"10.0.0.8"</code>\uff0c<code>BaseExceptionHandler.clientAddress(null)</code> \u7b49\u4e8e <code>"unknown"</code>\u3002 */
    @Test
    void remoteAddressIsUsedWithoutProxyHeaders() {
        AddressRequest request = new AddressRequest(null, null, "10.0.0.8");

        assertEquals("10.0.0.8", BaseExceptionHandler.clientAddress(request));
        assertEquals("unknown", BaseExceptionHandler.clientAddress(null));
    }

    /** \u5c06 <code>forwardedFor</code>\u3001<code>realIp</code>\u3001<code>remoteAddress</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    public static final class AddressRequest {
        /** \u6d4b\u8bd5\u8f6c\u53d1\u4ee3\u7406\u94fe\u89e3\u6790\u65f6\u63d0\u4f9b\u7684 X-Forwarded-For \u5185\u5bb9\u3002 */
        private final String forwardedFor;
        /** \u7528\u4e8e\u6821\u9a8c\u5ba2\u6237\u7aef\u5730\u5740\u89e3\u6790\u89c4\u5219\u7684 X-Real-IP \u8f93\u5165\u3002 */
        private final String realIp;
        /** \u6d4b\u8bd5\u6846\u67b6\u76f4\u63a5\u5f97\u5230\u7684\u8fde\u63a5\u5bf9\u7aef\u5730\u5740\u3002 */
        private final String remoteAddress;

        /**
         * \u8bbe\u7f6e <code>forwardedFor</code>\u3001<code>realIp</code>\u3001<code>remoteAddress</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>AddressRequest</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param forwardedFor \u7528\u4e8e\u521d\u59cb\u5316 <code>AddressRequest</code> \u7684 <code>String</code> \u503c
         * @param realIp \u7528\u4e8e\u521d\u59cb\u5316 <code>AddressRequest</code> \u7684 <code>String</code> \u503c
         * @param remoteAddress \u7528\u4e8e\u521d\u59cb\u5316 <code>AddressRequest</code> \u7684 <code>String</code> \u503c
         */
        AddressRequest(String forwardedFor, String realIp, String remoteAddress) {
            this.forwardedFor = forwardedFor;
            this.realIp = realIp;
            this.remoteAddress = remoteAddress;
        }

        /**
         * \u6309 <code>("X-Forwarded-For".equals(name))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>forwardedFor</code>\u3001<code>realIp</code>
         */
        public String getHeader(String name) {
            if ("X-Forwarded-For".equals(name)) {
                return forwardedFor;
            }
            if ("X-Real-IP".equals(name)) {
                return realIp;
            }
            return null;
        }

        /**
         * \u8bfb\u53d6<code>remoteAddress</code> \u5b57\u6bb5\u3002
         * @return <code>remoteAddress</code> \u5b57\u6bb5
         */
        public String getRemoteAddr() {
            return remoteAddress;
        }
    }
}
