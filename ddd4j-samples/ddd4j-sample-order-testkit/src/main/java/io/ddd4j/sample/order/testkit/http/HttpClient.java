package io.ddd4j.sample.order.testkit.http;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 使用现有 Apache HTTP 组件执行 Java 8 示例的集成请求。 */
public final class HttpClient {
    /** 保存 <code>connect Timeout</code> 对应的 <code>Duration</code> 数据。 */
    private final Duration connectTimeout;

    /**
     * 构造商品交易示例用于发送 HTTP 请求的客户端。
     *
     * @param connectTimeout 传入此操作的 <code>Duration</code> 类型 <code>connectTimeout</code>
     */
    private HttpClient(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * 按 <code>new Http Client</code> 的契约计算并返回结果。
     *
     * @return 返回表达式 <code>newBuilder().build()</code>
     */
    public static HttpClient newHttpClient() {
        return newBuilder().build();
    }

    /**
     * 按 <code>new Builder</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>Builder</code> 结果
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * 执行 <code>send</code>，调用 <code>Thread.interrupted</code>、<code>request.apacheRequest</code>、<code>PoolingHttpClientConnectionManagerBuilder.create</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param request 待发送或处理的 HTTP 请求
     * @param handler 传入此操作的 <code>HttpResponse.BodyHandler&lt;T&gt;</code> 类型 <code>handler</code>
     * @return 当前操作得到的 <code>HttpResponse&lt;T&gt;</code> 结果
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     * @throws InterruptedException 等待测试操作时被中断
     */
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("HTTP request interrupted");
        }
        URI uri;
        try {
            uri = request.apacheRequest().getUri();
        } catch (java.net.URISyntaxException failure) {
            throw new IOException(failure);
        }
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connector = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis())).build())
                .build();
        try (CloseableHttpClient client = HttpClients.custom().setConnectionManager(connector)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build())
                .disableRedirectHandling().disableAutomaticRetries().build()) {
            return client.execute(request.apacheRequest(), response -> new HttpResponse<>(
                    uri, response.getCode(), response.getHeaders(),
                    handler.read(response.getEntity()), response.getVersion().toString()));
        }
    }

    /**
     * 执行 <code>send Async</code>，调用 <code>CompletableFuture.supplyAsync</code>、<code>Thread.currentThread</code>、<code>result.whenComplete</code>。
     *
     * @param <T> 当前操作使用的泛型类型
     * @param request 待发送或处理的 HTTP 请求
     * @param handler 传入此操作的 <code>HttpResponse.BodyHandler&lt;T&gt;</code> 类型 <code>handler</code>
     * @return 按条件返回 <code>send(request, handler)</code>、<code>result</code>
     */
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        CompletableFuture<HttpResponse<T>> result = CompletableFuture.supplyAsync(() -> {
            try {
                return send(request, handler);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new CompletionException(failure);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
        result.whenComplete((response, failure) -> {
            if (result.isCancelled()) {
                request.apacheRequest().cancel();
            }
        });
        return result;
    }

    /** 集中保存 <code>HTTP_1_1</code>，供当前模块使用。 */
    public enum Version {
        /** 保存 <code>HTTP 1 1</code> 对应的 <code>Version</code> 数据。 */
        HTTP_1_1 }

    /** 集中保存 <code>connectTimeout</code>，供当前模块使用。 */
    public static final class Builder {
        /** 保存 <code>Duration.ofSeconds(20)</code>，供 <code>Builder</code> 的处理流程复用。 */
        private Duration connectTimeout = Duration.ofSeconds(20);

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param timeout 等待 HTTP 操作完成的时间上限
         * @return 返回表达式 <code>this</code>
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * 读取<code>this</code> 的当前值。
         *
         * @param version 传入此操作的 <code>Version</code> 类型 <code>version</code>
         * @return 返回表达式 <code>this</code>
         */
        public Builder version(Version version) {
            return this;
        }

        /**
         * 按 <code>build</code> 的契约计算并返回结果。
         *
         * @return 当前操作得到的 <code>HttpClient</code> 结果
         */
        public HttpClient build() {
            return new HttpClient(connectTimeout);
        }
    }
}
