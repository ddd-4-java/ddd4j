package io.ddd4j.sample.helidon.shiro;
import io.helidon.microprofile.server.Server;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import io.ddd4j.web.helidon.Ddd4jHelidonRequestFilter;
import io.ddd4j.web.helidon.Ddd4jHelidonResponseFilter;
import io.ddd4j.web.helidon.Ddd4jHelidonExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonIllegalStateExceptionMapper;
import java.util.Set;
/** 原生 MP 样例入口；主进程与测试共用相同 CDI 和 JAX-RS 装配。
 */
@ApplicationPath("/")
public class HelidonShiroApplication extends Application {
 @Override public Set<Class<?>> getClasses() { return Set.of(OrderResource.class,AuthenticationResource.class,NativeAuthFilter.class,
 Ddd4jHelidonRequestFilter.class,Ddd4jHelidonResponseFilter.class,Ddd4jHelidonExceptionMapper.class,Ddd4jHelidonIllegalStateExceptionMapper.class); }
 /**
 * @param port HTTP 端口，0 表示系统分配
 * @return 已启动服务
 */
 public static Server start(int port) { return Server.builder().port(port).addApplication(new HelidonShiroApplication()).build().start(); }
 /**
 * @param args 启动参数
 */
 public static void main(String[] args) { var server=start(Integer.getInteger("server.port",8080));Runtime.getRuntime().addShutdownHook(new Thread(server::stop)); }
}
