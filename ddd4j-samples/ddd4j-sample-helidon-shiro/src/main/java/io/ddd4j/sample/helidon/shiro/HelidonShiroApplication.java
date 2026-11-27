package io.ddd4j.sample.helidon.shiro;

import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.helidon.Ddd4jHelidonExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonIllegalStateExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonRequestFilter;
import io.ddd4j.web.helidon.Ddd4jHelidonResponseFilter;
import io.helidon.microprofile.server.Server;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

/**
 * 原生 MP 样例入口；主进程与测试共用相同 CDI 和 JAX-RS 装配。
 */
@ApplicationPath("/")
public class HelidonShiroApplication extends Application {
    /**
     * 当前 HTTP 示例使用的共用商品交易入口。
     */
    private final CatalogTradeResource catalog;

    /**
     * 从运行配置装配商品交易资源和 Shiro 演示。
     */
    public HelidonShiroApplication() {
        this(CatalogRuntime.start());
    }

    /**
     * 保存当前 JAX-RS 示例需注册的商品交易资源。
     *
     * @param endpoints 已装配的共用商品交易 HTTP 操作
     */
    HelidonShiroApplication(CatalogEndpoints endpoints) {
        this.catalog = new CatalogTradeResource(endpoints);
    }

    /**
     * @param port HTTP 端口，0 表示系统分配
     * @return 已启动服务
     */
    public static Server start(int port) {
        return Server.builder().port(port).addApplication(new HelidonShiroApplication()).build().start();
    }

    /**
     * @param args 启动参数
     */
    public static void main(String[] args) {
        var server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    /**
     * 暴露当前应用已装配好的商品交易资源实例。
     *
     * @return 当前应用传入 JAX-RS 的单例资源
     */
    @Override
    public Set<Object> getSingletons() {
        return Set.of(catalog);
    }

    /**
     * 声明由 JAX-RS 框架创建的资源、过滤器或异常映射器。
     *
     * @return 当前应用的 JAX-RS 组件类型
     */
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(AuthenticationResource.class, NativeAuthFilter.class,
                Ddd4jHelidonRequestFilter.class, Ddd4jHelidonResponseFilter.class, Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
