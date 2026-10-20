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
package io.ddd4j.web.quarkus;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证三线 Quarkus 过滤器读取远端地址时保留主机名与 IP 地址。
 */
class Ddd4jQuarkusWebFilterRemoteAddressTest {

    @Test
    void preservesUnresolvedPeerHost() throws Exception {
        assertEquals("localhost", remoteAddress("localhost"));
    }

    @Test
    void preservesPeerIpAddress() throws Exception {
        assertEquals("192.0.2.10", remoteAddress("192.0.2.10"));
    }

    private static String remoteAddress(String host) throws Exception {
        Ddd4jQuarkusWebFilter filter = new Ddd4jQuarkusWebFilter(new Ddd4jQuarkusWebConfiguration());
        SocketAddress address = SocketAddress.inetSocketAddress(8123, host);
        HttpServerRequest request = (HttpServerRequest) Proxy.newProxyInstance(
                HttpServerRequest.class.getClassLoader(), new Class<?>[]{HttpServerRequest.class},
                (proxy, called, args) -> "remoteAddress".equals(called.getName()) ? address : null);
        RoutingContext routingContext = (RoutingContext) Proxy.newProxyInstance(
                RoutingContext.class.getClassLoader(), new Class<?>[]{RoutingContext.class},
                (proxy, called, args) -> "request".equals(called.getName()) ? request : null);
        filter.routingContext = routingContext;

        Method method = Ddd4jQuarkusWebFilter.class.getDeclaredMethod("remoteAddress");
        method.setAccessible(true);
        return (String) method.invoke(filter);
    }
}
