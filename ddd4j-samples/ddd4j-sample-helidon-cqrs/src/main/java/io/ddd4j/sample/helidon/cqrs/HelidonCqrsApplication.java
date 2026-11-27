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
    private HelidonCqrsApplication() {
    }

    static Server start(int port, CatalogApi api) {
        return start(port, new HelidonCqrsJaxRsApplication(new CatalogTradeResource(api)));
    }

    public static Server start(int port) {
        return start(port, new HelidonCqrsJaxRsApplication());
    }

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

    public static void main(String[] args) {
        Server server = start(Integer.getInteger("server.port", 8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "helidon-catalog-cqrs-close"));
    }
}
