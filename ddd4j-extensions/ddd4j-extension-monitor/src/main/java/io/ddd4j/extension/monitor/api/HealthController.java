package io.ddd4j.extension.monitor.api;

/**
 * 健康检查
 *
 * <p>提供应用健康检查方法，供上层框架（如 Kubernetes 探针或负载均衡健康检测）调用。
 *
 * <p>本类为纯 Java 实现，不再依赖任何 Web 框架；具体如何将其暴露为 HTTP 接口由上层集成模块决定。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class HealthController {

    /**
     * 健康检查
     *
     * @return 健康状态字符串
     */
    public String health() {
        return "ok";
    }
}
