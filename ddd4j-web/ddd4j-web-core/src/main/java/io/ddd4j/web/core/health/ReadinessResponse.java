package io.ddd4j.web.core.health;

/**
 * Readiness HTTP 响应体，仅暴露整体状态，避免泄露下游依赖信息。
 *
 * <p>2026-09-04：从 2.0.x record 形式翻译为 JDK 8 兼容的传统 class（1.0.x 行适配）。</p>
 */
public final class ReadinessResponse {

    private final boolean ready;

    public ReadinessResponse(boolean ready) {
        this.ready = ready;
    }

    public boolean ready() { return ready; }

    /**
     * 返回探针应接收的 HTTP 状态码。
     *
     * @return 就绪时为 200，未就绪时为 503
     */
    public int httpStatus() {
        return ready ? 200 : 503;
    }
}