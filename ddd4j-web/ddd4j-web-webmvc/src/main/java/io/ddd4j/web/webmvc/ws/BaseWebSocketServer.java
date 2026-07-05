package io.ddd4j.web.webmvc.ws;

/**
 * WebSocket 服务端扩展点标记接口（JSR-346 / Spring WebSocket）。
 *
 * <p>业务方实现此接口并注册为 Spring Bean，{@link io.ddd4j.web.webmvc.config.BaseWebConfig}
 * 会在启动时自动调用 {@link #onStartup()} 钩子，把 WebSocket 端点注册到 Javalin/Spring Boot 容器。
 *
 * <p>使用抽象类（而非接口）以便未来添加通用钩子（如 {@code onShutdown}、{@code getPath} 等）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public abstract class BaseWebSocketServer {

    /**
     * WebSocket 路径（默认 {@code "/ws"}）。
     */
    public String getPath() {
        return "/ws";
    }

    /**
     * 启动钩子：业务方实现，把 WebSocket 端点注册到容器。
     */
    public abstract void onStartup();
}