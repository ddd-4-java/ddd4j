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
package io.ddd4j.web.webflux;

import io.ddd4j.web.webflux.config.LocalResourceProperteis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.config.ResourceChainRegistration;
import org.springframework.web.reactive.config.ResourceHandlerRegistration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.resource.ResourceResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证 WebFlux 静态资源处理器在三条兼容线上的共同配置契约。
 */
class DefaultWebFluxConfigurerContractTest {

    /** \u9a8c\u8bc1<code>resolver.getValue().getClass().getSimpleName().endsWith("WebJarsResourceResolver")</code>\u4e3a\u771f\u3002 */
    @Test
    void registersWebJarsResolverForVersionlessPaths() {
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class, RETURNS_SELF);
        ResourceChainRegistration chain = mock(ResourceChainRegistration.class);
        when(registry.addResourceHandler(anyString())).thenReturn(registration);
        when(registration.resourceChain(false)).thenReturn(chain);

        new DefaultWebFluxConfigurer(new LocalResourceProperteis()).addResourceHandlers(registry);

        verify(registry).addResourceHandler("/webjars/**");
        ArgumentCaptor<ResourceResolver> resolver = ArgumentCaptor.forClass(ResourceResolver.class);
        verify(chain).addResolver(resolver.capture());
        assertTrue(resolver.getValue().getClass().getSimpleName().endsWith("WebJarsResourceResolver"));
    }
}
