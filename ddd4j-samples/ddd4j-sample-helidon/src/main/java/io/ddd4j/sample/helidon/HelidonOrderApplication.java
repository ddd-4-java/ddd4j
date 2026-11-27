/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.helidon;

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
 * 共享 Order 业务内核的 Helidon MP JAX-RS 应用声明。
 */
@ApplicationPath("/")
public class HelidonOrderApplication extends Application {

    /** \u5411 JAX-RS \u5e94\u7528\u767b\u8bb0\u7684\u793a\u4f8b\u8d44\u6e90\u96c6\u5408\u3002 */
    private final Set<Object> resources;

    /** \u4ece\u8fd0\u884c\u914d\u7f6e\u7ec4\u88c5\u9ed8\u8ba4\u5546\u54c1\u4ea4\u6613 API\uff0c\u4f9b Helidon \u8def\u7531\u6ce8\u518c\u3002 */
    public HelidonOrderApplication() {
        this(CatalogRuntime.start());
    }

    /**
     * \u4fdd\u5b58\u5f53\u524d JAX-RS \u793a\u4f8b\u9700\u6ce8\u518c\u7684\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u3002
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     */
    private HelidonOrderApplication(CatalogEndpoints catalog) {
        resources = Set.of(new CatalogTradeResource(catalog));
    }

    /**
     * 启动共享订单样例。
     *
     * @param port HTTP 端口，0 由系统分配
     * @return 已启动的原生服务
     */
    public static Server start(int port) {
        return start(port, new HelidonOrderApplication());
    }

    /**
     * \u4f7f\u7528\u7ed9\u5b9a\u8d44\u6e90\u548c\u7aef\u53e3\u542f\u52a8 Helidon \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684 HTTP \u670d\u52a1\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param catalog \u5f53\u524d\u5546\u54c1\u4ea4\u6613\u5165\u53e3
     * @return \u5df2\u542f\u52a8\u7684 Helidon \u670d\u52a1
     */
    static Server start(int port, CatalogEndpoints catalog) {
        return start(port, new HelidonOrderApplication(catalog));
    }

    /**
     * \u4f7f\u7528\u7ed9\u5b9a\u8d44\u6e90\u548c\u7aef\u53e3\u542f\u52a8 Helidon \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684 HTTP \u670d\u52a1\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param application \u5df2\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u7684 JAX-RS \u5e94\u7528
     * @return \u5df2\u542f\u52a8\u7684 Helidon \u670d\u52a1
     */
    private static Server start(int port, HelidonOrderApplication application) {
        Server server = Server.builder().port(port).addApplication(application).build();
        try {
            return server.start();
        } catch (RuntimeException | Error failure) {
            try {
                server.stop();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * 启动默认示例，端口可用 server.port 配置。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        Server server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "helidon-order-shutdown"));
    }

    /**
     * \u8bfb\u53d6<code>resources</code> \u5b57\u6bb5\u3002
     * @return <code>resources</code> \u5b57\u6bb5
     */
    @Override
    public Set<Object> getSingletons() {
        return resources;
    }

    /**
     * \u58f0\u660e\u7531 JAX-RS \u6846\u67b6\u521b\u5efa\u7684\u8d44\u6e90\u3001\u8fc7\u6ee4\u5668\u6216\u5f02\u5e38\u6620\u5c04\u5668\u3002
     * @return \u5f53\u524d\u5e94\u7528\u7684 JAX-RS \u7ec4\u4ef6\u7c7b\u578b
     */
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(HelidonTokenResource.class, Ddd4jHelidonRequestFilter.class,
                Ddd4jHelidonResponseFilter.class, Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
