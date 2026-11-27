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

    /**
     * 注册到 JAX-RS 的 CQRS 商品交易资源集合。
     */
    private final Set<Object> catalog;

    /**
     * 注册默认的商品交易 CQRS 资源。
     */
    public HelidonCqrsJaxRsApplication() {
        this(new CatalogTradeResource());
    }

    /**
     * 保存当前 JAX-RS 示例需注册的商品交易资源。
     *
     * @param resource 待登记的商品交易资源
     */
    HelidonCqrsJaxRsApplication(CatalogTradeResource resource) {
        catalog = Set.of(resource);
    }

    /**
     * 读取<code>catalog</code> 字段。
     *
     * @return <code>catalog</code> 字段
     */
    @Override
    public Set<Object> getSingletons() {
        return catalog;
    }

    /**
     * 声明由 JAX-RS 框架创建的资源、过滤器或异常映射器。
     *
     * @return 当前应用的 JAX-RS 组件类型
     */
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(Ddd4jHelidonExceptionMapper.class, Ddd4jHelidonIllegalStateExceptionMapper.class);
    }
}
