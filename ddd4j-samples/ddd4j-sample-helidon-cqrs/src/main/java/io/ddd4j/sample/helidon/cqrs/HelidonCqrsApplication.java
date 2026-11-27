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
    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>HelidonCqrsApplication</code> \u5b9e\u4f8b\u3002 */
    private HelidonCqrsApplication() {
    }

    /**
     * \u4f7f\u7528\u7ed9\u5b9a\u8d44\u6e90\u548c\u7aef\u53e3\u542f\u52a8 Helidon \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684 HTTP \u670d\u52a1\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param api \u5171\u7528\u7684\u5546\u54c1\u4ea4\u6613 HTTP \u64cd\u4f5c
     * @return \u5df2\u542f\u52a8\u7684 Helidon \u670d\u52a1
     */
    static Server start(int port, CatalogApi api) {
        return start(port, new HelidonCqrsJaxRsApplication(new CatalogTradeResource(api)));
    }

    /**
     * \u4f7f\u7528\u9ed8\u8ba4 JAX-RS \u5e94\u7528\u542f\u52a8 Helidon CQRS \u793a\u4f8b\u3002
     * @param port HTTP \u76d1\u542c\u7aef\u53e3
     * @return \u5df2\u542f\u52a8\u7684 Helidon \u670d\u52a1
     */
    public static Server start(int port) {
        return start(port, new HelidonCqrsJaxRsApplication());
    }

    /**
     * \u4f7f\u7528\u7ed9\u5b9a\u8d44\u6e90\u548c\u7aef\u53e3\u542f\u52a8 Helidon \u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684 HTTP \u670d\u52a1\u3002
     * @param port \u5f53\u524d HTTP \u793a\u4f8b\u8981\u76d1\u542c\u7684\u7aef\u53e3
     * @param application \u5df2\u88c5\u914d\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u7684 JAX-RS \u5e94\u7528
     * @return \u5df2\u542f\u52a8\u7684 Helidon \u670d\u52a1
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
     * \u4ece\u7cfb\u7edf\u5c5e\u6027\u8bfb\u53d6\u7aef\u53e3\uff0c\u542f\u52a8 Helidon CQRS \u793a\u4f8b\u5e76\u6ce8\u518c\u5173\u95ed\u94a9\u5b50\u3002
     * @param args \u5e94\u7528\u542f\u52a8\u53c2\u6570
     */
    public static void main(String[] args) {
        Server server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "helidon-catalog-cqrs-close"));
    }
}
