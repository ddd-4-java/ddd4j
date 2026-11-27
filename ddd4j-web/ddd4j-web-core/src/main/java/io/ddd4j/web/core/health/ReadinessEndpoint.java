package io.ddd4j.web.core.health;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 将任意就绪状态转换为不包含下游诊断信息的 Web 响应。
 */
public final class ReadinessEndpoint {

    /**
     * Kubernetes 等上游探针使用的显式就绪路径。
     */
    public static final String PATH = "/-/ready";

    /** \u5f53\u524d\u8c03\u7528\u4f7f\u7528\u7684\u8fd0\u884c\u65f6\u5c31\u7eea\u72b6\u6001\u63d0\u4f9b\u51fd\u6570\u3002 */
    private final BooleanSupplier readinessSupplier;

    /**
     * \u521b\u5efa\u57fa\u4e8e\u5c31\u7eea\u72b6\u6001\u4f9b\u5e94\u5668\u7684\u7aef\u70b9\u3002
     * @param readinessSupplier \u5c31\u7eea\u72b6\u6001\u4f9b\u5e94\u5668
     */
    public ReadinessEndpoint(BooleanSupplier readinessSupplier) {
        this.readinessSupplier = Objects.requireNonNull(readinessSupplier,
                "readinessSupplier must not be null");
    }

    /**
     * 查询当前运行时是否可接收流量。
     *
     * @return 仅包含整体就绪状态的安全响应
     */
    public ReadinessResponse readiness() {
        return new ReadinessResponse(readinessSupplier.getAsBoolean());
    }
}
