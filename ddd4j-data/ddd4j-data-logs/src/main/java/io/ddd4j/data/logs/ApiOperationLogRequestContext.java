package io.ddd4j.data.logs;

import io.ddd4j.core.context.ThreadContext;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * 操作日志专用的请求采集边界；默认复用 WebContextScope 已绑定的中立 ThreadContext 键。
 * HTTP 适配器可通过 ServiceLoader 提供实现，日志本身不加载 Servlet 或 WebMVC 类型。
 */
public interface ApiOperationLogRequestContext {
    /**
     * 查找当前日志模块类加载器可见的首个 HTTP 适配器，否则使用中立线程上下文。
     * 适配器必须在没有当前 HTTP 请求时回退到默认方法；一次调用只使用一个适配器。
     *
     * @return 请求采集器
     */
    static ApiOperationLogRequestContext current() {
        Iterator<ApiOperationLogRequestContext> providers = ServiceLoader.load(ApiOperationLogRequestContext.class,
                ApiOperationLogRequestContext.class.getClassLoader()).iterator();
        return providers.hasNext() ? providers.next() : new ApiOperationLogRequestContext() {
        };
    }

    /**
     * @return 当前请求 ID；没有请求上下文时返回 null
     */
    default String requestId() {
        return ThreadContext.get("request-id");
    }

    /**
     * @return 当前请求路径；没有请求上下文时返回 null
     */
    default String requestUri() {
        return ThreadContext.get("http-path");
    }

    /**
     * @return 当前客户端 IP；没有请求上下文时返回 null
     */
    default String clientIp() {
        return ThreadContext.get("client-ip");
    }

    /**
     * 判断参数是否应写入日志，HTTP 适配器保留原有 Servlet 参数筛选语义。
     *
     * @param argument 连接点参数
     * @return 是否记录该参数
     */
    default boolean includeArgument(Object argument) {
        return true;
    }
}
