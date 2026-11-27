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

    /**
     * \u901a\u8fc7 <code>(String)method.invoke(filter)</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param host \u4f20\u7ed9 <code>SocketAddress.inetSocketAddress()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>(String)method.invoke(filter)</code>
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>remoteAddress("localhost")</code> \u7b49\u4e8e <code>"localhost"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void preservesUnresolvedPeerHost() throws Exception {
        assertEquals("localhost", remoteAddress("localhost"));
    }

    /**
     * \u9a8c\u8bc1<code>remoteAddress("192.0.2.10")</code> \u7b49\u4e8e <code>"192.0.2.10"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void preservesPeerIpAddress() throws Exception {
        assertEquals("192.0.2.10", remoteAddress("192.0.2.10"));
    }
}
