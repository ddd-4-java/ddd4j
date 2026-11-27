package io.ddd4j.web.webmvc.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 <code>BaseExceptionHandlerAddress</code> 在 <code>remoteAddressIsUsed</code> 等场景中的行为。
 */
class BaseExceptionHandlerAddressTest {

    /**
     * 验证<code>BaseExceptionHandler.clientAddress(request)</code> 等于 <code>"203.0.113.10"</code>。
     */
    @Test
    void forwardedAddressTakesPrecedenceInExceptionLog() {
        AddressRequest request = new AddressRequest("203.0.113.10, 10.0.0.7", "198.51.100.20", "10.0.0.8");

        assertEquals("203.0.113.10", BaseExceptionHandler.clientAddress(request));
    }

    /**
     * 验证<code>BaseExceptionHandler.clientAddress(request)</code> 等于 <code>"10.0.0.8"</code>，<code>BaseExceptionHandler.clientAddress(null)</code> 等于 <code>"unknown"</code>。
     */
    @Test
    void remoteAddressIsUsedWithoutProxyHeaders() {
        AddressRequest request = new AddressRequest(null, null, "10.0.0.8");

        assertEquals("10.0.0.8", BaseExceptionHandler.clientAddress(request));
        assertEquals("unknown", BaseExceptionHandler.clientAddress(null));
    }

    /**
     * 将 <code>forwardedFor</code>、<code>realIp</code>、<code>remoteAddress</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class AddressRequest {
        /**
         * 测试转发代理链解析时提供的 X-Forwarded-For 内容。
         */
        private final String forwardedFor;
        /**
         * 用于校验客户端地址解析规则的 X-Real-IP 输入。
         */
        private final String realIp;
        /**
         * 测试框架直接得到的连接对端地址。
         */
        private final String remoteAddress;

        /**
         * 设置 <code>forwardedFor</code>、<code>realIp</code>、<code>remoteAddress</code>，用于构造 <code>AddressRequest</code> 测试实例。
         *
         * @param forwardedFor  用于初始化 <code>AddressRequest</code> 的 <code>String</code> 值
         * @param realIp        用于初始化 <code>AddressRequest</code> 的 <code>String</code> 值
         * @param remoteAddress 用于初始化 <code>AddressRequest</code> 的 <code>String</code> 值
         */
        AddressRequest(String forwardedFor, String realIp, String remoteAddress) {
            this.forwardedFor = forwardedFor;
            this.realIp = realIp;
            this.remoteAddress = remoteAddress;
        }

        /**
         * 按 <code>("X-Forwarded-For".equals(name))</code> 等实际条件选择当前用例返回的结果。
         *
         * @param name 当前测试指定的对象或属性名称
         * @return 根据测试条件返回的可能值，包括 <code>forwardedFor</code>、<code>realIp</code>
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
         * 读取<code>remoteAddress</code> 字段。
         *
         * @return <code>remoteAddress</code> 字段
         */
        public String getRemoteAddr() {
            return remoteAddress;
        }
    }
}
