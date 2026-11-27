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
    /** \u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u7684\u539f\u751f\u5bf9\u8c61\uff0c\u7528\u4e8e\u9002\u914d Sa-Token \u8bf7\u6c42\u884c\u4e3a\u3002 */
    private final RoutingContext request;

    /**
     * \u7528\u5f53\u524d\u6846\u67b6\u8bf7\u6c42\u521b\u5efa Sa-Token \u539f\u751f\u8bf7\u6c42\u9002\u914d\u5668\u3002
     * @param request \u5f53\u524d\u8fdb\u5165 Sa-Token \u7684\u539f\u751f HTTP \u8bf7\u6c42
     */
    SaHttpContext(RoutingContext request) {
        this.request = request;
    }

    /**
     * \u628a\u539f\u751f HTTP \u54cd\u5e94\u5305\u88c5\u4e3a Sa-Token \u53ef\u5199\u5165\u7684\u54cd\u5e94\u3002
     * @param response \u5f53\u524d\u539f\u751f HTTP \u54cd\u5e94\u6216\u54cd\u5e94\u6784\u5efa\u5668
     * @return Sa-Token \u54cd\u5e94\u9002\u914d\u5668
     */
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

    /**
     * \u4ee5\u5f53\u524d\u539f\u751f\u8bf7\u6c42\u5c5e\u6027\u63d0\u4f9b Sa-Token \u8bf7\u6c42\u7ea7\u5b58\u50a8\u3002
     * @param request \u5f53\u524d\u8fdb\u5165 Sa-Token \u7684\u539f\u751f HTTP \u8bf7\u6c42
     * @return Sa-Token \u8bf7\u6c42\u5b58\u50a8\u9002\u914d\u5668
     */
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

    /**
     * \u8bfb\u53d6<code>request</code> \u5b57\u6bb5\u3002
     * @return <code>request</code> \u5b57\u6bb5
     */
    @Override
    public Object getSource() {
        return request;
    }

    /**
     * \u4ece\u539f\u751f\u8bf7\u6c42\u4e2d\u53d6\u51fa\u4e00\u4e2a\u547d\u540d\u53c2\u6570\u3002
     * @param name \u5f85\u8bfb\u53d6\u7684\u53c2\u6570\u3001Cookie\u3001\u8bf7\u6c42\u5934\u6216\u5176\u4ed6\u914d\u7f6e\u9879\u540d\u79f0
     * @return \u5339\u914d\u7684\u53c2\u6570\u503c\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    @Override
    public String getParam(String name) {
        return request.request().getParam(name);
    }

    /**
     * \u5217\u4e3e\u5f53\u524d\u8bf7\u6c42\u63d0\u4f9b\u7684\u53c2\u6570\u540d\u79f0\u3002
     * @return \u539f\u751f\u8bf7\u6c42\u53c2\u6570\u540d\u5217\u8868
     */
    @Override
    public Collection<String> getParamNames() {
        return request.queryParams().names();
    }

    /**
     * \u5c06\u539f\u751f\u8bf7\u6c42\u53c2\u6570\u8f6c\u4e3a\u5355\u503c\u6620\u5c04\u3002
     * @return \u542b\u8bf7\u6c42\u53c2\u6570\u540d\u79f0\u548c\u4ee3\u8868\u503c\u7684\u6620\u5c04
     */
    @Override
    public Map<String, String> getParamMap() {
        return request.queryParams().entries().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, last) -> first));
    }

    /**
     * \u6309\u540d\u79f0\u8bfb\u53d6\u539f\u751f\u8bf7\u6c42\u5934\u3002
     * @param name \u5f85\u8bfb\u53d6\u7684\u53c2\u6570\u3001Cookie\u3001\u8bf7\u6c42\u5934\u6216\u5176\u4ed6\u914d\u7f6e\u9879\u540d\u79f0
     * @return \u8bf7\u6c42\u5934\u5b57\u7b26\u4e32\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    @Override
    public String getHeader(String name) {
        String bearer = request.request().getHeader("Authorization");
        if (name.equals(StpUtil.getTokenName()) && Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return bearer.substring(7).trim();
        }
        return request.request().getHeader(name);
    }

    /**
     * \u6309\u540d\u79f0\u8bfb\u53d6\u8bf7\u6c42 Cookie\u3002
     * @param name \u5f85\u8bfb\u53d6\u7684\u53c2\u6570\u3001Cookie\u3001\u8bf7\u6c42\u5934\u6216\u5176\u4ed6\u914d\u7f6e\u9879\u540d\u79f0
     * @return Cookie \u6587\u672c\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    @Override
    public String getCookieValue(String name) {
        return java.util.Optional.ofNullable(request.request().getCookie(name)).map(io.vertx.core.http.Cookie::getValue).orElse(null);
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u4e2d\u6307\u5b9a\u540d\u79f0\u7684\u9996\u4e2a Cookie \u503c\u3002
     * @param name \u5f85\u8bfb\u53d6\u7684\u53c2\u6570\u3001Cookie\u3001\u8bf7\u6c42\u5934\u6216\u5176\u4ed6\u914d\u7f6e\u9879\u540d\u79f0
     * @return \u5339\u914d\u7684 Cookie \u503c\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    @Override
    public String getCookieFirstValue(String name) {
        return getCookieValue(name);
    }

    /**
     * \u4f7f\u7528\u5f53\u524d\u9002\u914d\u5668\u7684 Cookie \u67e5\u627e\u89c4\u5219\u8bfb\u53d6\u6307\u5b9a\u503c\u3002
     * @param name \u5f85\u8bfb\u53d6\u7684\u53c2\u6570\u3001Cookie\u3001\u8bf7\u6c42\u5934\u6216\u5176\u4ed6\u914d\u7f6e\u9879\u540d\u79f0
     * @return \u5339\u914d\u7684 Cookie \u503c\uff0c\u7f3a\u5c11\u65f6\u4e3a\u7a7a
     */
    @Override
    public String getCookieLastValue(String name) {
        return getCookieValue(name);
    }

    /**
     * \u8bfb\u53d6\u5f53\u524d\u8bf7\u6c42\u5728\u670d\u52a1\u4e2d\u7684\u8def\u5f84\u3002
     * @return HTTP \u8bf7\u6c42\u8def\u5f84
     */
    @Override
    public String getRequestPath() {
        return request.request().path();
    }

    /**
     * \u53d6\u5f97\u539f\u751f\u8bf7\u6c42\u5730\u5740\u3002
     * @return \u5f53\u524d\u6846\u67b6\u63d0\u4f9b\u7684\u8bf7\u6c42 URL
     */
    @Override
    public String getUrl() {
        return request.request().absoluteURI();
    }

    /**
     * \u8bfb\u53d6\u539f\u751f\u8bf7\u6c42\u4f7f\u7528\u7684 HTTP \u65b9\u6cd5\u3002
     * @return \u5f53\u524d\u8bf7\u6c42\u7684\u65b9\u6cd5\u540d
     */
    @Override
    public String getMethod() {
        return request.request().method().name();
    }

    /**
     * \u53d6\u5f97\u5f53\u524d\u539f\u751f HTTP \u8bf7\u6c42\u7684\u4e3b\u673a\u4fe1\u606f\u3002
     * @return \u6846\u67b6\u62a5\u544a\u7684\u8bf7\u6c42\u4e3b\u673a
     */
    @Override
    public String getHost() {
        return request.request().getHeader("Host");
    }

    /**
     * \u793a\u4f8b\u4ec5\u6ce8\u518c JSON \u8def\u7531\uff0c\u8bf7\u6c42\u8f6c\u53d1\u4f1a\u89e6\u53d1\u4e0d\u652f\u6301\u5f02\u5e38\u3002
     * @param path \u8bf7\u6c42\u8f6c\u53d1\u76ee\u6807\u8def\u5f84
     * @return \u8bf7\u6c42\u8f6c\u53d1\u6ca1\u6709\u6b63\u5e38\u8fd4\u56de\u503c
     * @throws UnsupportedOperationException \u5f53\u524d\u793a\u4f8b\u4e0d\u652f\u6301 Servlet \u5f0f\u8bf7\u6c42\u8f6c\u53d1
     */
    @Override
    public Object forward(String path) {
        throw new UnsupportedOperationException("sample supports synchronous JSON routes only");
    }
}
