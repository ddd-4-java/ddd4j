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

    /**
     * 向 JAX-RS 应用登记的示例资源集合。
     */
    private final Set<Object> resources;

    /**
     * 从运行配置组装默认商品交易 API，供 Helidon 路由注册。
     */
    public HelidonOrderApplication() {
        this(CatalogRuntime.start());
    }

    /**
     * 保存当前 JAX-RS 示例需注册的商品交易资源。
     *
     * @param catalog 当前商品交易入口
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
     * 使用给定资源和端口启动 Helidon 商品交易示例的 HTTP 服务。
     *
     * @param port    当前 HTTP 示例要监听的端口
     * @param catalog 当前商品交易入口
     * @return 已启动的 Helidon 服务
     */
    static Server start(int port, CatalogEndpoints catalog) {
        return start(port, new HelidonOrderApplication(catalog));
    }

    /**
     * 使用给定资源和端口启动 Helidon 商品交易示例的 HTTP 服务。
     *
     * @param port        当前 HTTP 示例要监听的端口
     * @param application 已装配商品交易资源的 JAX-RS 应用
     * @return 已启动的 Helidon 服务
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
     * 读取<code>resources</code> 字段。
     *
     * @return <code>resources</code> 字段
     */
    @Override
    public Set<Object> getSingletons() {
        return resources;
    }

    /**
     * 声明由 JAX-RS 框架创建的资源、过滤器或异常映射器。
     *
     * @return 当前应用的 JAX-RS 组件类型
     */
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(HelidonTokenResource.class, Ddd4jHelidonRequestFilter.class,
                Ddd4jHelidonResponseFilter.class, Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
