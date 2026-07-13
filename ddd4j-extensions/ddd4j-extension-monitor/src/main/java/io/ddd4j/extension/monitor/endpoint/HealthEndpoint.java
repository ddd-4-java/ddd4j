package io.ddd4j.extension.monitor.endpoint;

/**
 * 健康检查端点。
 *
 * <p>提供应用健康检查方法，供上层框架（如 Kubernetes 探针、负载均衡健康检测）调用。
 * 纯 Java 实现，与 Web 框架无关；具体如何将其暴露为 HTTP 接口由上层集成模块决定。
 *
 * <p>v2.x：原 {@code api/HealthController} 重命名/迁移至 {@code endpoint/} 包。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class HealthEndpoint {

    /**
     * 健康检查。
     *
     * @return 健康状态字符串
     */
    public String health() {
        return "ok";
    }
}
