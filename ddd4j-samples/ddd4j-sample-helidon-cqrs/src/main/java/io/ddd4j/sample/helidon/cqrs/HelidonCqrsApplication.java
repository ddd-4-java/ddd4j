package io.ddd4j.sample.helidon.cqrs;

import io.helidon.microprofile.server.Server;

/**
 * Helidon MP CQRS 集成示例启动入口。
 *
 * <p>CQRS 组件通过 {@link HelidonCqrsBeans} 以 CDI 方式装配，
 * {@code OrderResource} 通过 {@code @Inject} 获取依赖。
 */
public class HelidonCqrsApplication {

    public static void main(String[] args) {
        Server.builder()
                .addApplication(new HelidonCqrsJaxRsApplication())
                .build()
                .start();
    }
}
