package io.ddd4j.sample.helidon.satoken;

import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 当前 Helidon JAX-RS 请求的原生 Sa-Token HTTP 适配；不使用 mock request 或 Subject。
 */
final class SaHttpContext implements SaRequest {
    private final ContainerRequestContext request;

    SaHttpContext(ContainerRequestContext request) {
        this.request = request;
    }

    static SaResponse response(Response.ResponseBuilder response) {
        return new SaResponse() {
            @Override
            public Object getSource() {
                return response;
            }

            @Override
            public SaResponse setStatus(int status) {
                response.status(status);
                return this;
            }

            @Override
            public SaResponse setHeader(String name, String value) {
                response.header(name, value);
                return this;
            }

            @Override
            public SaResponse addHeader(String name, String value) {
                response.header(name, value);
                return this;
            }

            @Override
            public Object redirect(String url) {
                return response.status(302).header("Location", url);
            }
        };
    }

    static SaStorage storage(ContainerRequestContext request) {
        return new SaStorage() {
            private final Map<String, Object> attributes = new HashMap<>();

            @Override
            public Object getSource() {
                return request;
            }

            @Override
            public Object get(String key) {
                return attributes.get(key);
            }

            @Override
            public SaStorage set(String key, Object value) {
                attributes.put(key, value);
                return this;
            }

            @Override
            public SaStorage delete(String key) {
                attributes.remove(key);
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
        return request.getUriInfo().getQueryParameters().getFirst(name);
    }

    @Override
    public Collection<String> getParamNames() {
        return request.getUriInfo().getQueryParameters().keySet();
    }

    @Override
    public Map<String, String> getParamMap() {
        return request.getUriInfo().getQueryParameters().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }

    @Override
    public String getHeader(String name) {
        String bearer = request.getHeaderString("Authorization");
        if (name.equals(StpUtil.getTokenName()) && Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return bearer.substring(7).trim();
        }
        return request.getHeaderString(name);
    }

    @Override
    public String getCookieValue(String name) {
        return java.util.Optional.ofNullable(request.getCookies().get(name)).map(javax.ws.rs.core.Cookie::getValue).orElse(null);
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
        return request.getUriInfo().getPath();
    }

    @Override
    public String getUrl() {
        return request.getUriInfo().getRequestUri().toString();
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getHost() {
        return request.getHeaderString("Host");
    }

    @Override
    public Object forward(String path) {
        throw new UnsupportedOperationException("sample supports synchronous JSON routes only");
    }
}
