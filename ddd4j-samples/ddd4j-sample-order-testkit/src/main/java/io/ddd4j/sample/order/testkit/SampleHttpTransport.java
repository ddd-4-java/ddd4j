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
        private final int statusCode;
        private final String body;

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
