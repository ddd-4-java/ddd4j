package io.ddd4j.sample.javalin.cqrs;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证查询失败响应保留既有字符串错误码及 HTTP 状态。 */
class MissingQueryCodeContractTest {
    private Javalin app;

    @BeforeEach
    void startApp() {
        app = TestSupport.start();
    }

    @AfterEach
    void stopApp() {
        app.stop();
    }

    @Test
    void missingGoodsIdKeepsStringCode() throws Exception {
        assertMissing("/api/goods/query/by-id/999999999", 404);
    }

    @Test
    void missingGoodsCodeKeepsStringCode() throws Exception {
        assertMissing("/api/goods/query/by-code/absent-code", 200);
    }

    @Test
    void missingOrderKeepsStringCode() throws Exception {
        assertMissing("/api/orders/query/detail/absent-order", 200);
    }

    private void assertMissing(String path, int status) throws Exception {
        TestHttpClient.HttpResponse<String> response = new TestHttpClient().get(
                "http://127.0.0.1:" + app.port() + path);
        JsonNode code = new ObjectMapper().readTree(response.body()).get("code");
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(code.isTextual(), response.body());
        assertEquals("404", code.asText());
    }
}
