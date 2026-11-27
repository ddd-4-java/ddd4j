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

import io.ddd4j.web.helidon.Ddd4jHelidonExceptionMapper;
import io.ddd4j.web.helidon.Ddd4jHelidonIllegalStateExceptionMapper;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import java.util.Set;

/**
 * Helidon JAX-RS 应用声明。
 */
@ApplicationPath("/")
public class HelidonCqrsJaxRsApplication extends Application {

    /** \u6ce8\u518c\u5230 JAX-RS \u7684 CQRS \u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u96c6\u5408\u3002 */
    private final Set<Object> catalog;

    /** \u6ce8\u518c\u9ed8\u8ba4\u7684\u5546\u54c1\u4ea4\u6613 CQRS \u8d44\u6e90\u3002 */
    public HelidonCqrsJaxRsApplication() {
        this(new CatalogTradeResource());
    }

    /**
     * \u4fdd\u5b58\u5f53\u524d JAX-RS \u793a\u4f8b\u9700\u6ce8\u518c\u7684\u5546\u54c1\u4ea4\u6613\u8d44\u6e90\u3002
     * @param resource \u5f85\u767b\u8bb0\u7684\u5546\u54c1\u4ea4\u6613\u8d44\u6e90
     */
    HelidonCqrsJaxRsApplication(CatalogTradeResource resource) {
        catalog = Set.of(resource);
    }

    /**
     * \u8bfb\u53d6<code>catalog</code> \u5b57\u6bb5\u3002
     * @return <code>catalog</code> \u5b57\u6bb5
     */
    @Override
    public Set<Object> getSingletons() {
        return catalog;
    }

    /**
     * \u58f0\u660e\u7531 JAX-RS \u6846\u67b6\u521b\u5efa\u7684\u8d44\u6e90\u3001\u8fc7\u6ee4\u5668\u6216\u5f02\u5e38\u6620\u5c04\u5668\u3002
     * @return \u5f53\u524d\u5e94\u7528\u7684 JAX-RS \u7ec4\u4ef6\u7c7b\u578b
     */
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(Ddd4jHelidonExceptionMapper.class, Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
