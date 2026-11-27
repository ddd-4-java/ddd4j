package io.ddd4j.sample.order.testkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * 使用原生 JDK HttpClient 的真实样例 HTTP 传输。
 */
public final class SampleHttpTransport {

    /** \u9650\u5236\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private SampleHttpTransport() {
    }

    /**
     * 向指定地址发起请求，并返回响应状态和正文。
     *
     * @param url \u670d\u52a1\u5730\u5740\u4e0e\u8bf7\u6c42\u8def\u5f84
     * @param method HTTP \u65b9\u6cd5
     * @param headers \u9a71\u52a8\u7684\u7f3a\u7701\u8bf7\u6c42\u5934
     * @param additional \u5355\u4e2a\u8bf7\u6c42\u7684\u9644\u52a0\u5934
     * @param body JSON \u8bf7\u6c42\u4f53\uff0c\u53ef\u4e3a\u7a7a
     * @return \u5b9e\u9645\u54cd\u5e94\u72b6\u6001\u7801\u548c\u6b63\u6587
     * @throws IOException \u8bf7\u6c42\u4f20\u8f93\u5931\u8d25\u6216\u8c03\u7528\u7ebf\u7a0b\u88ab\u4e2d\u65ad
     */
    public static Response request(String url, String method, Map<String, String> headers,
                                   Map<String, String> additional, String body) throws IOException {
        try {
            return request(HttpClient.newHttpClient(), url, method, headers, additional, body);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", failure);
        }
    }

    /**
     * \u590d\u7528\u9a71\u52a8\u539f\u6709\u5ba2\u6237\u7aef\u914d\u7f6e\uff0c\u5e76\u5c06\u539f\u751f\u4e2d\u65ad\u4ea4\u7ed9\u9a71\u52a8\u539f\u6765\u7684\u5f02\u5e38\u8fb9\u754c\u5904\u7406\u3002
     *
     * @param client \u53d1\u8d77\u8bf7\u6c42\u7684 HTTP \u5ba2\u6237\u7aef
     * @param url \u6d4b\u8bd5\u8bf7\u6c42 URL
     * @param method \u8bf7\u6c42\u4f7f\u7528\u7684 HTTP \u65b9\u6cd5
     * @param headers \u8bf7\u6c42\u7684\u57fa\u7840 HTTP \u5934
     * @param additional \u8ffd\u52a0\u5230\u8bf7\u6c42\u7684 HTTP \u5934
     * @param body \u8bf7\u6c42\u6b63\u6587\uff0c\u65e0\u6b63\u6587\u65f6\u4e3a\u7a7a
     * @return \u4f20\u8f93\u5c42\u8fd4\u56de\u7684\u72b6\u6001\u7801\u53ca\u54cd\u5e94\u6b63\u6587
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    static Response request(HttpClient client, String url, String method, Map<String, String> headers,
                            Map<String, String> additional, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        headers.forEach(builder::header);
        additional.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.method(method, Objects.isNull(body)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    /**
     * 实际 HTTP 响应的状态与正文。
     */
    public static final class Response {
        /** HTTP \u54cd\u5e94\u72b6\u6001\u7801\u3002 */
        private final int statusCode;
        /** HTTP \u54cd\u5e94\u6b63\u6587\u3002 */
        private final String body;

        /**
         * \u4fdd\u5b58\u6d4b\u8bd5\u5ba2\u6237\u7aef\u63a5\u6536\u5230\u7684\u72b6\u6001\u7801\u4e0e\u54cd\u5e94\u6b63\u6587\u3002
         * @param statusCode HTTP \u54cd\u5e94\u72b6\u6001\u7801
         * @param body HTTP \u54cd\u5e94\u6b63\u6587
         */
        private Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        /** @return HTTP \u54cd\u5e94\u72b6\u6001\u7801 */
        public int statusCode() {
            return statusCode;
        }

        /** @return \u6309\u54cd\u5e94\u58f0\u660e\u5b57\u7b26\u96c6\u89e3\u7801\u7684\u6b63\u6587\uff0c\u7f3a\u7701 UTF-8 */
        public String body() {
            return body;
        }
    }
}
