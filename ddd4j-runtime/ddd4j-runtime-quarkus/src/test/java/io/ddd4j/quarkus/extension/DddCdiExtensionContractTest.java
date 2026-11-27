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
package io.ddd4j.quarkus.extension;

import io.ddd4j.quarkus.annotation.ddd.DomainService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/** 三线共同的 DDD CDI 作用域注册契约。 */
class DddCdiExtensionContractTest {

    /** \u9a8c\u8bc1<code>scope.getValue().annotationType()</code> \u7b49\u4e8e <code>ApplicationScoped.class</code>\u3002 */
    @Test
    void addsApplicationScopeToDddService() {
        ProcessAnnotatedType<Object> event = event();
        AnnotatedType<Object> type = event.getAnnotatedType();
        AnnotatedTypeConfigurator<Object> configurator = mock(AnnotatedTypeConfigurator.class);
        when(type.isAnnotationPresent(DomainService.class)).thenReturn(true);
        when(type.getJavaClass()).thenReturn(Object.class);
        when(event.configureAnnotatedType()).thenReturn(configurator);

        new DddCdiExtension().processAnnotatedType(event);

        ArgumentCaptor<Annotation> scope = ArgumentCaptor.forClass(Annotation.class);
        verify(configurator).add(scope.capture());
        assertEquals(ApplicationScoped.class, scope.getValue().annotationType());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>event()</code>\u3001<code>event.getAnnotatedType()</code>\u3001<code>type.isAnnotationPresent()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void preservesExistingApplicationScope() {
        ProcessAnnotatedType<Object> event = event();
        AnnotatedType<Object> type = event.getAnnotatedType();
        when(type.isAnnotationPresent(DomainService.class)).thenReturn(true);
        when(type.isAnnotationPresent(ApplicationScoped.class)).thenReturn(true);

        new DddCdiExtension().processAnnotatedType(event);

        verify(event, never()).configureAnnotatedType();
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>event()</code>\u3001<code>never()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void ignoresNonDddType() {
        ProcessAnnotatedType<Object> event = event();

        new DddCdiExtension().processAnnotatedType(event);

        verify(event, never()).configureAnnotatedType();
    }

    /**
     * \u8bfb\u53d6 <code>event</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>event</code>
     */
    @SuppressWarnings("unchecked")
    private ProcessAnnotatedType<Object> event() {
        ProcessAnnotatedType<Object> event = mock(ProcessAnnotatedType.class);
        AnnotatedType<Object> type = mock(AnnotatedType.class);
        when(event.getAnnotatedType()).thenReturn(type);
        return event;
    }
}
