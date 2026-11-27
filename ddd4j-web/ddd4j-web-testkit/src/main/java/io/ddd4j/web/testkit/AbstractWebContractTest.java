/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.web.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.web.core.context.WebHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 所有 Web 适配器必须通过的响应、鉴权、上下文与幂等契约。
 */
public abstract class AbstractWebContractTest {

    /**
     * 合同客户端测试有效会话使用的 Bearer 头。
     */
    private static final String VALID_BEARER = "Bearer contract-valid-token";
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * @return 待验证的 Web 契约客户端
     */
    protected abstract WebContractClient client();

    /**
     * 验证普通成功与创建成功响应保持约定状态码和 JSON 包装。
     *
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Test
    void shouldExposeStableSuccessAndCreatedEnvelopes() throws Exception {
        assertSuccess(client().request("GET", WebContractPaths.SUCCESS, Map.of(), null), 200);
        assertSuccess(client().request("POST", WebContractPaths.CREATED, Map.of(), "{}"), 201);
    }

    /**
     * 验证不同异常映射到预期 HTTP 状态和统一 JSON 错误结构。
     *
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Test
    void shouldExposeTranslatedErrorStatusesAndEnvelopes() throws Exception {
        assertError(WebContractPaths.BAD_REQUEST, 400);
        assertError(WebContractPaths.FORBIDDEN, 403);
        assertError(WebContractPaths.NOT_FOUND, 404);
        assertError(WebContractPaths.CONFLICT, 409);
        assertError(WebContractPaths.UNSUPPORTED_MEDIA_TYPE, 415);
        assertError(WebContractPaths.UNPROCESSABLE_ENTITY, 422);
        assertError(WebContractPaths.TOO_MANY_REQUESTS, 429);
        assertError(WebContractPaths.INTERNAL_SERVER_ERROR, 500);
    }

    /**
     * 验证公开与受保护路径分别按策略接受或拒绝令牌。
     *
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Test
    void shouldEnforceBearerAuthenticationAccordingToPolicy() throws Exception {
        assertError(client().request("GET", WebContractPaths.PROTECTED, Map.of(), null), 401);
        assertError(client().request("GET", WebContractPaths.PROTECTED,
                Map.of(WebHeaders.AUTHORIZATION, "Bearer invalid-token"), null), 401);
        assertSuccess(client().request("GET", WebContractPaths.PROTECTED,
                Map.of(WebHeaders.AUTHORIZATION, VALID_BEARER), null), 200);
        assertSuccess(client().request("GET", WebContractPaths.PUBLIC, Map.of(), null), 200);
    }

    /**
     * 验证字面请求头透传以及响应携带请求标识。
     *
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Test
    void shouldPropagateRequestHeadersAndReturnRequestId() throws Exception {
        Map<String, String> headers = Map.of(
                WebHeaders.REQUEST_ID, "request-contract-1",
                WebHeaders.TRACE_ID, "trace-contract-1",
                WebHeaders.TENANT_ID, "tenant-contract-1");
        WebContractResponse response = client().request("GET", WebContractPaths.CONTEXT, headers, null);
        JsonNode data = body(response).path("data");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.firstHeader(WebHeaders.REQUEST_ID)).contains("request-contract-1");
        assertThat(data.path("requestId").asText()).isEqualTo("request-contract-1");
        assertThat(data.path("traceId").asText()).isEqualTo("trace-contract-1");
        assertThat(data.path("tenantId").asText()).isEqualTo("tenant-contract-1");
    }

    /**
     * 验证一个请求结束后后续请求不能读取前一次的认证与上下文。
     *
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Test
    void shouldNotLeakContextBetweenRequests() throws Exception {
        client().request("GET", WebContractPaths.CONTEXT,
                Map.of(WebHeaders.TENANT_ID, "tenant-first-request"), null);
        WebContractResponse second = client().request("GET", WebContractPaths.CONTEXT, Map.of(), null);
        JsonNode tenantId = body(second).path("data").path("tenantId");

        assertThat(tenantId.isNull() || tenantId.isMissingNode()).isTrue();
    }

    /**
     * 验证重复幂等键被拒绝并返回预期状态。
     *
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    @Test
    void shouldRejectDuplicateIdempotencyKey() throws Exception {
        String key = "contract-" + UUID.randomUUID();
        Map<String, String> headers = Map.of(WebHeaders.IDEMPOTENCY_KEY, key);

        assertSuccess(client().request("POST", WebContractPaths.IDEMPOTENT, headers, "{}"), 200);
        assertError(client().request("POST", WebContractPaths.IDEMPOTENT, headers, "{}"), 409);
    }

    /**
     * 验证错误 HTTP 状态和 JSON 包装符合跨框架合同。
     *
     * @param path           当前被处理的 HTTP 请求路径
     * @param expectedStatus 合同用例要求匹配的 HTTP 状态码
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    private void assertError(String path, int expectedStatus) throws Exception {
        assertError(client().request("GET", path, Map.of(), null), expectedStatus);
    }

    /**
     * 验证错误 HTTP 状态和 JSON 包装符合跨框架合同。
     *
     * @param response       当前准备写出的 HTTP 响应
     * @param expectedStatus 合同用例要求匹配的 HTTP 状态码
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    private void assertError(WebContractResponse response, int expectedStatus) throws Exception {
        JsonNode body = body(response);

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(body.path("code").asInt()).isEqualTo(expectedStatus);
        assertThat(body.path("msg").asText()).isNotBlank();
    }

    /**
     * 验证成功 HTTP 状态和 JSON 包装符合跨框架合同。
     *
     * @param response       当前准备写出的 HTTP 响应
     * @param expectedStatus 合同用例要求匹配的 HTTP 状态码
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    private void assertSuccess(WebContractResponse response, int expectedStatus) throws Exception {
        JsonNode body = body(response);

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").isMissingNode()).isFalse();
    }

    /**
     * 解析框架返回的 JSON 响应正文。
     *
     * @param response 当前准备写出的 HTTP 响应
     * @return 用于合同断言的 JSON 对象
     * @throws Exception 当前框架请求处理、消息读写或参数转换失败
     */
    private JsonNode body(WebContractResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.body());
    }
}
