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

import io.ddd4j.web.core.context.WebHeaders;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 所有 Web 适配器必须通过的响应、鉴权、上下文与幂等契约。
 */
public abstract class AbstractWebContractTest {

    /** \u5408\u540c\u5ba2\u6237\u7aef\u6d4b\u8bd5\u6709\u6548\u4f1a\u8bdd\u4f7f\u7528\u7684 Bearer \u5934\u3002 */
    private static final String VALID_BEARER = "Bearer contract-valid-token";
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** @return \u5f85\u9a8c\u8bc1\u7684 Web \u5951\u7ea6\u5ba2\u6237\u7aef */
    protected abstract WebContractClient client();

    /**
     * \u9a8c\u8bc1\u666e\u901a\u6210\u529f\u4e0e\u521b\u5efa\u6210\u529f\u54cd\u5e94\u4fdd\u6301\u7ea6\u5b9a\u72b6\u6001\u7801\u548c JSON \u5305\u88c5\u3002
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Test
    void shouldExposeStableSuccessAndCreatedEnvelopes() throws Exception {
        assertSuccess(client().request("GET", WebContractPaths.SUCCESS, Map.of(), null), 200);
        assertSuccess(client().request("POST", WebContractPaths.CREATED, Map.of(), "{}"), 201);
    }

    /**
     * \u9a8c\u8bc1\u4e0d\u540c\u5f02\u5e38\u6620\u5c04\u5230\u9884\u671f HTTP \u72b6\u6001\u548c\u7edf\u4e00 JSON \u9519\u8bef\u7ed3\u6784\u3002
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
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
     * \u9a8c\u8bc1\u516c\u5f00\u4e0e\u53d7\u4fdd\u62a4\u8def\u5f84\u5206\u522b\u6309\u7b56\u7565\u63a5\u53d7\u6216\u62d2\u7edd\u4ee4\u724c\u3002
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
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
     * \u9a8c\u8bc1\u5b57\u9762\u8bf7\u6c42\u5934\u900f\u4f20\u4ee5\u53ca\u54cd\u5e94\u643a\u5e26\u8bf7\u6c42\u6807\u8bc6\u3002
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
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
     * \u9a8c\u8bc1\u4e00\u4e2a\u8bf7\u6c42\u7ed3\u675f\u540e\u540e\u7eed\u8bf7\u6c42\u4e0d\u80fd\u8bfb\u53d6\u524d\u4e00\u6b21\u7684\u8ba4\u8bc1\u4e0e\u4e0a\u4e0b\u6587\u3002
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
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
     * \u9a8c\u8bc1\u91cd\u590d\u5e42\u7b49\u952e\u88ab\u62d2\u7edd\u5e76\u8fd4\u56de\u9884\u671f\u72b6\u6001\u3002
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    @Test
    void shouldRejectDuplicateIdempotencyKey() throws Exception {
        String key = "contract-" + UUID.randomUUID();
        Map<String, String> headers = Map.of(WebHeaders.IDEMPOTENCY_KEY, key);

        assertSuccess(client().request("POST", WebContractPaths.IDEMPOTENT, headers, "{}"), 200);
        assertError(client().request("POST", WebContractPaths.IDEMPOTENT, headers, "{}"), 409);
    }

    /**
     * \u9a8c\u8bc1\u9519\u8bef HTTP \u72b6\u6001\u548c JSON \u5305\u88c5\u7b26\u5408\u8de8\u6846\u67b6\u5408\u540c\u3002
     * @param path \u5f53\u524d\u88ab\u5904\u7406\u7684 HTTP \u8bf7\u6c42\u8def\u5f84
     * @param expectedStatus \u5408\u540c\u7528\u4f8b\u8981\u6c42\u5339\u914d\u7684 HTTP \u72b6\u6001\u7801
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    private void assertError(String path, int expectedStatus) throws Exception {
        assertError(client().request("GET", path, Map.of(), null), expectedStatus);
    }

    /**
     * \u9a8c\u8bc1\u9519\u8bef HTTP \u72b6\u6001\u548c JSON \u5305\u88c5\u7b26\u5408\u8de8\u6846\u67b6\u5408\u540c\u3002
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param expectedStatus \u5408\u540c\u7528\u4f8b\u8981\u6c42\u5339\u914d\u7684 HTTP \u72b6\u6001\u7801
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    private void assertError(WebContractResponse response, int expectedStatus) throws Exception {
        JsonNode body = body(response);

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(body.path("code").asInt()).isEqualTo(expectedStatus);
        assertThat(body.path("msg").asText()).isNotBlank();
    }

    /**
     * \u9a8c\u8bc1\u6210\u529f HTTP \u72b6\u6001\u548c JSON \u5305\u88c5\u7b26\u5408\u8de8\u6846\u67b6\u5408\u540c\u3002
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @param expectedStatus \u5408\u540c\u7528\u4f8b\u8981\u6c42\u5339\u914d\u7684 HTTP \u72b6\u6001\u7801
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    private void assertSuccess(WebContractResponse response, int expectedStatus) throws Exception {
        JsonNode body = body(response);

        assertThat(response.status()).isEqualTo(expectedStatus);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").isMissingNode()).isFalse();
    }

    /**
     * \u89e3\u6790\u6846\u67b6\u8fd4\u56de\u7684 JSON \u54cd\u5e94\u6b63\u6587\u3002
     * @param response \u5f53\u524d\u51c6\u5907\u5199\u51fa\u7684 HTTP \u54cd\u5e94
     * @return \u7528\u4e8e\u5408\u540c\u65ad\u8a00\u7684 JSON \u5bf9\u8c61
     * @throws Exception \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u5904\u7406\u3001\u6d88\u606f\u8bfb\u5199\u6216\u53c2\u6570\u8f6c\u6362\u5931\u8d25
     */
    private JsonNode body(WebContractResponse response) throws Exception {
        return OBJECT_MAPPER.readTree(response.body());
    }
}
