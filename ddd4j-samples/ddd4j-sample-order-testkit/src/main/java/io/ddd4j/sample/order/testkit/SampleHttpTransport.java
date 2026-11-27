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

    /**
     * 限制工具类实例化。
     */
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
        try {
            return request(HttpClient.newHttpClient(), url, method, headers, additional, body);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", failure);
        }
    }

    /**
     * 复用驱动原有客户端配置，并将原生中断交给驱动原来的异常边界处理。
     *
     * @param client     发起请求的 HTTP 客户端
     * @param url        测试请求 URL
     * @param method     请求使用的 HTTP 方法
     * @param headers    请求的基础 HTTP 头
     * @param additional 追加到请求的 HTTP 头
     * @param body       请求正文，无正文时为空
     * @return 传输层返回的状态码及响应正文
     * @throws IOException          读取或写入测试数据时发生 I/O 错误
     * @throws InterruptedException 等待测试操作时被中断
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
        /**
         * HTTP 响应状态码。
         */
        private final int statusCode;
        /**
         * HTTP 响应正文。
         */
        private final String body;

        /**
         * 保存测试客户端接收到的状态码与响应正文。
         *
         * @param statusCode HTTP 响应状态码
         * @param body       HTTP 响应正文
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
