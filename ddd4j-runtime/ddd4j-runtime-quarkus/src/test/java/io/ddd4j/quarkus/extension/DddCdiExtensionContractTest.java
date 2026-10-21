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

    @Test
    void preservesExistingApplicationScope() {
        ProcessAnnotatedType<Object> event = event();
        AnnotatedType<Object> type = event.getAnnotatedType();
        when(type.isAnnotationPresent(DomainService.class)).thenReturn(true);
        when(type.isAnnotationPresent(ApplicationScoped.class)).thenReturn(true);

        new DddCdiExtension().processAnnotatedType(event);

        verify(event, never()).configureAnnotatedType();
    }

    @Test
    void ignoresNonDddType() {
        ProcessAnnotatedType<Object> event = event();

        new DddCdiExtension().processAnnotatedType(event);

        verify(event, never()).configureAnnotatedType();
    }

    @SuppressWarnings("unchecked")
    private ProcessAnnotatedType<Object> event() {
        ProcessAnnotatedType<Object> event = mock(ProcessAnnotatedType.class);
        AnnotatedType<Object> type = mock(AnnotatedType.class);
        when(event.getAnnotatedType()).thenReturn(type);
        return event;
    }
}
