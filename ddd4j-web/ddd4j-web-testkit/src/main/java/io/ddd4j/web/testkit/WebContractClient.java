package io.ddd4j.web.testkit;

import java.util.Map;

/**
 * Web 适配器测试提供的框架无关请求客户端。
 */
public interface WebContractClient {

    /**
     * \u53d1\u8d77\u5951\u7ea6\u6d4b\u8bd5\u8bf7\u6c42\u3002
     * @param method HTTP \u65b9\u6cd5
     * @param path \u8bf7\u6c42\u8def\u5f84
     * @param headers \u8bf7\u6c42\u5934
     * @param body \u8bf7\u6c42\u4f53
     * @return \u54cd\u5e94\u5feb\u7167
     */
    WebContractResponse request(String method, String path, Map<String, String> headers, String body);
}
