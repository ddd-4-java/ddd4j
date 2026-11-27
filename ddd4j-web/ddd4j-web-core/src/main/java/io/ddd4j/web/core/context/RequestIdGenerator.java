package io.ddd4j.web.core.context;

import java.util.UUID;

/**
 * 为缺失请求标识的请求生成服务端标识。
 */
@FunctionalInterface
public interface RequestIdGenerator {

    /**
     * \u751f\u6210\u4e00\u4e2a\u8bf7\u6c42\u6807\u8bc6\u3002
     * @return \u751f\u6210\u5f97\u5230\u7684\u8bf7\u6c42\u6807\u8bc6\u5b57\u7b26\u4e32
     */
    String generate();

    /**
     * \u521b\u5efa\u57fa\u4e8e\u968f\u673a UUID \u7684\u8bf7\u6c42\u6807\u8bc6\u751f\u6210\u5668\u3002
     * @return \u4ee5\u968f\u673a UUID \u4f5c\u4e3a\u6807\u8bc6\u7684\u751f\u6210\u5668\u5b9e\u73b0
     */
    static RequestIdGenerator uuid() {
        return () -> UUID.randomUUID().toString();
    }
}
