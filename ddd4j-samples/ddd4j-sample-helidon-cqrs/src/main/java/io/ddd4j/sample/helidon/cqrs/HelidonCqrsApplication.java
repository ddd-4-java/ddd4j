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

import io.ddd4j.helidon.Ddd4jHelidonConfig;
import io.helidon.microprofile.server.Server;

/**
 * Helidon MP CQRS 集成示例启动入口。
 *
 * <p>CQRS 组件通过 {@link HelidonCqrsBeans} 以 CDI 方式装配，
 * {@code OrderResource} 通过 {@code @Inject} 获取依赖。
 */
public class HelidonCqrsApplication {

    static boolean durableProjectionEnabled() {
        return new Ddd4jHelidonConfig()
                .value("ddd4j.cqrs.projection.enabled", Boolean.class)
                .orElse(false);
    }

    /**
 * @param port HTTP 端口
 * @return 已启动真实 MP 服务
 */
    public static Server start(int port) {
        return Server.builder().port(port).addApplication(new HelidonCqrsJaxRsApplication()).build().start();
    }
    /**
     * 启动 Helidon CQRS 示例应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        Server server = start(Integer.getInteger("server.port",8080));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
