package io.ddd4j.sample.vertx.satoken;

import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import io.vertx.ext.web.RoutingContext;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 当前 Vert.x 请求的原生 Sa-Token HTTP 适配；不使用 mock request 或 Subject。
 */
final class SaHttpContext implements SaRequest {
    private final RoutingContext request;

    SaHttpContext(RoutingContext request) {
        this.request = request;
    }

    static SaResponse response(RoutingContext response) {
        return new SaResponse() {
            @Override
            public Object getSource() {
                return response;
            }

            @Override
            public SaResponse setStatus(int status) {
                response.response().setStatusCode(status);
                return this;
            }

            @Override
            public SaResponse setHeader(String name, String value) {
                response.response().putHeader(name, value);
                return this;
            }

            @Override
            public SaResponse addHeader(String name, String value) {
                response.response().putHeader(name, value);
                return this;
            }

            @Override
            public Object redirect(String url) {
                return response.response().setStatusCode(302).putHeader("Location", url);
            }
        };
    }

    static SaStorage storage(RoutingContext request) {
        return new SaStorage() {
            @Override
            public Object getSource() {
                return request;
            }

            @Override
            public Object get(String key) {
                return request.get(key);
            }

            @Override
            public SaStorage set(String key, Object value) {
                request.put(key, value);
                return this;
            }

            @Override
            public SaStorage delete(String key) {
                request.remove(key);
                return this;
            }
        };
    }

    @Override
    public Object getSource() {
        return request;
    }

    @Override
    public String getParam(String name) {
        return request.request().getParam(name);
    }

    @Override
    public Collection<String> getParamNames() {
        return request.queryParams().names();
    }

    @Override
    public Map<String, String> getParamMap() {
        return request.queryParams().entries().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, last) -> first));
    }

    @Override
    public String getHeader(String name) {
        String bearer = request.request().getHeader("Authorization");
        if (name.equals(StpUtil.getTokenName()) && Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return bearer.substring(7).trim();
        }
        return request.request().getHeader(name);
    }

    @Override
    public String getCookieValue(String name) {
        return java.util.Optional.ofNullable(request.request().getCookie(name)).map(io.vertx.core.http.Cookie::getValue).orElse(null);
    }

    @Override
    public String getCookieFirstValue(String name) {
        return getCookieValue(name);
    }

    @Override
    public String getCookieLastValue(String name) {
        return getCookieValue(name);
    }

    @Override
    public String getRequestPath() {
        return request.request().path();
    }

    @Override
    public String getUrl() {
        return request.request().absoluteURI();
    }

    @Override
    public String getMethod() {
        return request.request().method().name();
    }

    @Override
    public String getHost() {
        return request.request().getHeader("Host");
    }

    @Override
    public Object forward(String path) {
        throw new UnsupportedOperationException("sample supports synchronous JSON routes only");
    }
}
