package io.ddd4j.sample.order.testkit;

import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 使用 Apache HttpClient 的 Java 8 样例 HTTP 传输。
 */
public final class SampleHttpTransport {

    /** \u9650\u5236\u5de5\u5177\u7c7b\u5b9e\u4f8b\u5316\u3002 */
    private SampleHttpTransport() {
    }

    /**
     * @param url        服务地址与请求路径
     * @param method     HTTP 方法
     * @param headers    驱动的缺省请求头
     * @param additional 单个请求的附加头
     * @param body       JSON 请求体，可为空
     * @return 实际响应状态码和正文
     * @throws IOException 请求传输失败或调用线程被中断
     */
    public static Response request(String url, String method, Map<String, String> headers,
                                   Map<String, String> additional, String body) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("HTTP request interrupted", new InterruptedException());
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        headers.forEach(builder::header);
        additional.forEach(builder::header);
        HttpRequest request = builder.method(method, body).build();
        CompletableFuture<HttpResponse<String>> exchange = HttpClient.newHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> response = exchange.get();
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException failure) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("HTTP request failed", cause);
        }
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

        /**
         * @return HTTP 响应状态码
         */
        public int statusCode() {
            return statusCode;
        }

        /**
         * @return 按响应声明字符集解码的正文，缺省 UTF-8
         */
        public String body() {
            return body;
        }
    }
}
