package io.ddd4j.web.testkit;

import java.util.List;
import java.util.Map;

/**
 * Web 契约测试使用的最小响应快照。
 */
public record WebContractResponse(int status, Map<String, List<String>> headers, String body) {
}
