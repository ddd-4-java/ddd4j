package io.ddd4j.sample.micronaut.satoken;

import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 当前 Micronaut 请求的原生 Sa-Token HTTP 适配；不使用 mock request 或 Subject。
 */
final class SaHttpContext implements SaRequest {
    /**
     * 从 Authorization 头中分离令牌使用的协议前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 当前框架请求的原生对象，用于适配 Sa-Token 请求行为。
     */
    private final HttpRequest<?> request;

    /**
     * 用当前框架请求创建 Sa-Token 原生请求适配器。
     *
     * @param request 当前进入 Sa-Token 的原生 HTTP 请求
     */
    SaHttpContext(HttpRequest<?> request) {
        this.request = request;
    }

    /**
     * 把原生 HTTP 响应包装为 Sa-Token 可写入的响应。
     *
     * @param response 当前原生 HTTP 响应或响应构建器
     * @return Sa-Token 响应适配器
     */
    static SaResponse response(MutableHttpResponse<?> response) {
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
                response.getHeaders().add(name, value);
                return this;
            }

            @Override
            public Object redirect(String url) {
                return response.status(302).header("Location", url);
            }
        };
    }

    /**
     * 以当前原生请求属性提供 Sa-Token 请求级存储。
     *
     * @param request 当前进入 Sa-Token 的原生 HTTP 请求
     * @return Sa-Token 请求存储适配器
     */
    static SaStorage storage(HttpRequest<?> request) {
        return new SaStorage() {
            @Override
            public Object getSource() {
                return request;
            }

            @Override
            public Object get(String key) {
                return request.getAttributes().get(key, Object.class).orElse(null);
            }

            @Override
            public SaStorage set(String key, Object value) {
                request.setAttribute(key, value);
                return this;
            }

            @Override
            public SaStorage delete(String key) {
                request.getAttributes().remove(key);
                return this;
            }
        };
    }

    /**
     * 读取<code>request</code> 字段。
     *
     * @return <code>request</code> 字段
     */
    @Override
    public Object getSource() {
        return request;
    }

    /**
     * 从原生请求中取出一个命名参数。
     *
     * @param name 待读取的参数、Cookie、请求头或其他配置项名称
     * @return 匹配的参数值，缺少时为空
     */
    @Override
    public String getParam(String name) {
        return request.getParameters().get(name);
    }

    /**
     * 列举当前请求提供的参数名称。
     *
     * @return 原生请求参数名列表
     */
    @Override
    public Collection<String> getParamNames() {
        return request.getParameters().names();
    }

    /**
     * 将原生请求参数转为单值映射。
     *
     * @return 含请求参数名称和代表值的映射
     */
    @Override
    public Map<String, String> getParamMap() {
        return request.getParameters().asMap(String.class, String.class);
    }

    /**
     * 按名称读取原生请求头。
     *
     * @param name 待读取的参数、Cookie、请求头或其他配置项名称
     * @return 请求头字符串，缺少时为空
     */
    @Override
    public String getHeader(String name) {
        String bearer = request.getHeaders().get("Authorization");
        if (name.equals(StpUtil.getTokenName()) && Objects.nonNull(bearer)
                && bearer.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return bearer.substring(BEARER_PREFIX.length()).trim();
        }
        return request.getHeaders().get(name);
    }

    /**
     * 按名称读取请求 Cookie。
     *
     * @param name 待读取的参数、Cookie、请求头或其他配置项名称
     * @return Cookie 文本，缺少时为空
     */
    @Override
    public String getCookieValue(String name) {
        return request.getCookies().findCookie(name).map(io.micronaut.http.cookie.Cookie::getValue).orElse(null);
    }

    /**
     * 读取当前请求中指定名称的首个 Cookie 值。
     *
     * @param name 待读取的参数、Cookie、请求头或其他配置项名称
     * @return 匹配的 Cookie 值，缺少时为空
     */
    @Override
    public String getCookieFirstValue(String name) {
        return getCookieValue(name);
    }

    /**
     * 使用当前适配器的 Cookie 查找规则读取指定值。
     *
     * @param name 待读取的参数、Cookie、请求头或其他配置项名称
     * @return 匹配的 Cookie 值，缺少时为空
     */
    @Override
    public String getCookieLastValue(String name) {
        return getCookieValue(name);
    }

    /**
     * 读取当前请求在服务中的路径。
     *
     * @return HTTP 请求路径
     */
    @Override
    public String getRequestPath() {
        return request.getPath();
    }

    /**
     * 取得原生请求地址。
     *
     * @return 当前框架提供的请求 URL
     */
    @Override
    public String getUrl() {
        return request.getUri().toString();
    }

    /**
     * 读取原生请求使用的 HTTP 方法。
     *
     * @return 当前请求的方法名
     */
    @Override
    public String getMethod() {
        return request.getMethodName();
    }

    /**
     * 取得当前原生 HTTP 请求的主机信息。
     *
     * @return 框架报告的请求主机
     */
    @Override
    public String getHost() {
        return request.getHeaders().get("Host");
    }

    /**
     * 示例仅注册 JSON 路由，请求转发会触发不支持异常。
     *
     * @param path 请求转发目标路径
     * @return 请求转发没有正常返回值
     * @throws UnsupportedOperationException 当前示例不支持 Servlet 式请求转发
     */
    @Override
    public Object forward(String path) {
        throw new UnsupportedOperationException("sample supports synchronous JSON routes only");
    }
}
