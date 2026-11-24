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

import io.ddd4j.web.helidon.Ddd4jHelidonExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonIllegalStateExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonRequestFilter;
import io.ddd4j.web.helidon.Ddd4jHelidonResponseFilter;
import io.helidon.microprofile.server.Server;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

/**
 * 共享 Order 业务内核的 Helidon MP JAX-RS 应用声明。
 */
@ApplicationPath("/")
public class HelidonOrderApplication extends Application {

    /**
     * 启动共享订单样例。
     *
     * @param port HTTP 端口，0 由系统分配
     * @return 已启动的原生服务
     */
    public static Server start(int port) {
        Server server = Server.builder().port(port).addApplication(new HelidonOrderApplication()).build();
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

    @Override
    public Set<Class<?>> getClasses() {
        return new HashSet<>(Arrays.<Class<?>>asList(HelidonOrderResource.class,
                Ddd4jHelidonRequestFilter.class,
                Ddd4jHelidonResponseFilter.class,
                Ddd4jHelidonExceptionMapper.class,
                Ddd4jHelidonIllegalStateExceptionMapper.class));
    }
}
