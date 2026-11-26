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
    private final Duration connectTimeout;

    private HttpClient(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public static HttpClient newHttpClient() {
        return newBuilder().build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

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

    public enum Version { HTTP_1_1 }

    public static final class Builder {
        private Duration connectTimeout = Duration.ofSeconds(20);

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder version(Version version) {
            return this;
        }

        public HttpClient build() {
            return new HttpClient(connectTimeout);
        }
    }
}
