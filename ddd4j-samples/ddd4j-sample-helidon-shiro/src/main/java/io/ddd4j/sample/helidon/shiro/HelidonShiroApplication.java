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
    /** \u5f53\u524d HTTP \u793a\u4f8b\u4f7f\u7528\u7684\u5171\u7528\u5546\u54c1\u4ea4\u6613\u5165\u53e3\u3002 */
    private final CatalogTradeResource catalog;

    /** \u4ece\u8fd0\u884c\u914d\u7f6e\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u548c Shiro \u6f14\u793a\u3002 */
    public HelidonShiroApplication() {
        this(CatalogRuntime.start());
    }

    /**
     * \u4fdd\u5b58\u5f53\u524d JAX-RS \u793a\u4f8b\u9700\u6ce8\u518c\u7684\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u3002
     * @param endpoints \u5df2\u88c5\u914d\u7684\u5171\u7528\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     */
    HelidonShiroApplication(CatalogEndpoints endpoints) {
        this.catalog = new CatalogTradeResource(endpoints);
    }

    /**
     * @param port HTTP \u7aef\u53e3\uff0c0 \u8868\u793a\u7cfb\u7edf\u5206\u914d
     * @return \u5df2\u542f\u52a8\u670d\u52a1
     */
    public static Server start(int port) {
        return Server.builder().port(port).addApplication(new HelidonShiroApplication()).build().start();
    }

    /** @param args \u542f\u52a8\u53c2\u6570 */
    public static void main(String[] args) {
        var server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    /**
     * \u66b4\u9732\u5f53\u524d\u5e94\u7528\u5df2\u88c5\u914d\u597d\u7684\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u5b9e\u4f8b\u3002
     * @return \u5f53\u524d\u5e94\u7528\u4f20\u5165 JAX-RS \u7684\u5355\u4f8b\u8d44\u6e90
     */
    @Override
    public Set<Object> getSingletons() {
        return Set.of(catalog);
    }

    /**
     * \u58f0\u660e\u7531 JAX-RS \u6846\u67b6\u521b\u5efa\u7684\u8d44\u6e90\u3001\u8fc7\u6ee4\u5668\u6216\u5f02\u5e38\u6620\u5c04\u5668\u3002
     * @return \u5f53\u524d\u5e94\u7528\u7684 JAX-RS \u7ec4\u4ef6\u7c7b\u578b
     */
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(AuthenticationResource.class, NativeAuthFilter.class,
                Ddd4jHelidonRequestFilter.class, Ddd4jHelidonResponseFilter.class, Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
