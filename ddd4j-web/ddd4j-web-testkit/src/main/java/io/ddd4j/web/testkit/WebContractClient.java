package io.ddd4j.web.testkit;

import java.util.Map;

/**
 * Web 适配器测试提供的框架无关请求客户端。
 */
public interface WebContractClient {

    WebContractResponse request(String method, String path, Map<String, String> headers, String body);
}
