package io.ddd4j.sample.helidon.shiro;

import com.google.common.collect.ImmutableSet;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.web.helidon.Ddd4jHelidonExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonIllegalStateExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonRequestFilter;
import io.ddd4j.web.helidon.Ddd4jHelidonResponseFilter;
import io.helidon.microprofile.server.Server;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import java.util.Set;

/**
 * 原生 MP 样例入口；主进程与测试共用相同 CDI 和 JAX-RS 装配。
 */
@ApplicationPath("/")
public class HelidonShiroApplication extends Application {
    private final CatalogTradeResource catalog;

    public HelidonShiroApplication() {
        this(CatalogRuntime.start());
    }

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
        Server server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    @Override
    public Set<Object> getSingletons() {
        return ImmutableSet.of(catalog);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.of(AuthenticationResource.class, NativeAuthFilter.class,
                Ddd4jHelidonRequestFilter.class, Ddd4jHelidonResponseFilter.class, Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
