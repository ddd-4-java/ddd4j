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
package io.ddd4j.web.webmvc;

import io.ddd4j.web.webmvc.config.LocalResourceProperteis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.config.annotation.ResourceChainRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.ResourceResolver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 MVC 静态资源处理器的共同 WebJars 配置契约。
 */
class DefaultWebMvcConfigurerWebJarsTest {

    @Test
    void registersWebJarsResolverForVersionlessPaths() {
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class, RETURNS_SELF);
        ResourceChainRegistration chain = mock(ResourceChainRegistration.class);
        when(registry.addResourceHandler(anyString())).thenReturn(registration);
        when(registration.resourceChain(false)).thenReturn(chain);

        new DefaultWebMvcConfigurer(new LocalResourceProperteis(), null, null).addResourceHandlers(registry);

        verify(registry).addResourceHandler("/webjars/**");
        ArgumentCaptor<ResourceResolver> resolver = ArgumentCaptor.forClass(ResourceResolver.class);
        verify(chain).addResolver(resolver.capture());
        assertTrue(resolver.getValue().getClass().getSimpleName().endsWith("WebJarsResourceResolver"));
    }
}
