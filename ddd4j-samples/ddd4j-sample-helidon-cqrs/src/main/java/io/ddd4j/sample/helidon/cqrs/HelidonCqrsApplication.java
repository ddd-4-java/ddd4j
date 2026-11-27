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
package io.ddd4j.sample.helidon.cqrs;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.helidon.microprofile.server.Server;

/**
 * Helidon MP 四表商品交易和快照投影。
 */
public final class HelidonCqrsApplication {
    /**
     * 限制调用方直接创建 <code>HelidonCqrsApplication</code> 实例。
     */
    private HelidonCqrsApplication() {
    }

    /**
     * 使用给定资源和端口启动 Helidon 商品交易示例的 HTTP 服务。
     *
     * @param port 当前 HTTP 示例要监听的端口
     * @param api  共用的商品交易 HTTP 操作
     * @return 已启动的 Helidon 服务
     */
    static Server start(int port, CatalogApi api) {
        return start(port, new HelidonCqrsJaxRsApplication(new CatalogTradeResource(api)));
    }

    /**
     * 使用默认 JAX-RS 应用启动 Helidon CQRS 示例。
     *
     * @param port HTTP 监听端口
     * @return 已启动的 Helidon 服务
     */
    public static Server start(int port) {
        return start(port, new HelidonCqrsJaxRsApplication());
    }

    /**
     * 使用给定资源和端口启动 Helidon 商品交易示例的 HTTP 服务。
     *
     * @param port        当前 HTTP 示例要监听的端口
     * @param application 已装配商品交易资源的 JAX-RS 应用
     * @return 已启动的 Helidon 服务
     */
    private static Server start(int port, HelidonCqrsJaxRsApplication application) {
        Server server = Server.builder().port(port).addApplication(application).build();
        try {
            return server.start();
        } catch (RuntimeException | Error failure) {
            try {
                server.stop();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * 从系统属性读取端口，启动 Helidon CQRS 示例并注册关闭钩子。
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        Server server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "helidon-catalog-cqrs-close"));
    }
}
