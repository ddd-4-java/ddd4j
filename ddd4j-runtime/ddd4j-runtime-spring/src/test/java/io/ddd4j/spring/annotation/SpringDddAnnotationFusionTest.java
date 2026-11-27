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
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 DDD 注解的 Spring 组合注解及聚合根标记契约。
 */
@DisplayName("ddd4j-runtime-spring DDD 注解与 Spring 元注解融合验收")
class SpringDddAnnotationFusionTest {

    /**
     * 核对<code>annotationType.getAnnotation(DDDAnnotation.class)</code>非空，<code>annotationType.getAnnotation(springAnnotationType)</code>非空。
     *
     * @param annotationType       传给 <code>assertNotNull()</code> 的 <code>Class&lt;? extends Annotation&gt;</code> 输入
     * @param springAnnotationType 传给 <code>assertNotNull()</code> 的 <code>Class&lt;? extends Annotation&gt;</code> 输入
     */
    private static void assertMetaAnnotations(Class<? extends Annotation> annotationType,
                                              Class<? extends Annotation> springAnnotationType) {
        assertNotNull(annotationType.getAnnotation(DDDAnnotation.class));
        assertNotNull(annotationType.getAnnotation(springAnnotationType));
    }

    /**
     * 调用 <code>Class.forName()</code> 执行当前夹具操作。
     *
     * @param className 传给 <code>Class.forName()</code> 的 <code>String</code> 输入
     * @return 根据测试条件返回的可能值，包括 <code>Class.forName(className)</code>、<code>null</code>
     */
    private static Class<?> loadOptional(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /**
     * 验证 DDD 组件注解公开相应的 Spring 元注解。
     */
    @Test
    void dddAnnotationsShouldExposeSpringMetaAnnotations() {
        assertMetaAnnotations(DomainService.class, Service.class);
        assertMetaAnnotations(DomainRepository.class, Repository.class);
        assertMetaAnnotations(ApplicationService.class, Service.class);
        assertMetaAnnotations(QueryService.class, Service.class);
        assertMetaAnnotations(CommandExecutor.class, Component.class);
        assertMetaAnnotations(DomainEntity.class, Component.class);
        assertMetaAnnotations(DomainValueObject.class, Component.class);
        assertMetaAnnotations(DomainGateway.class, Component.class);
        assertMetaAnnotations(DomainAssembler.class, Component.class);
        assertMetaAnnotations(DomainConverter.class, Component.class);
    }

    /**
     * 验证<code>AnnotationUtils.findAnnotation(TestDomainService.class, Service.class)</code>非空，<code>AnnotationUtils.findAnnotation(TestDomainService.class, DDDAnnotation.class)</code>非空，<code>AnnotationUtils.findAnnotation(TestDomainRepository.class, Repository.class)</code>非空。
     */
    @Test
    void springShouldResolveComposedAnnotations() {
        /** 领域服务组合注解样例。 */
        @DomainService
        class TestDomainService {
        }

        /** 领域仓储组合注解样例。 */
        @DomainRepository
        class TestDomainRepository {
        }

        assertNotNull(AnnotationUtils.findAnnotation(TestDomainService.class, Service.class));
        assertNotNull(AnnotationUtils.findAnnotation(TestDomainService.class, DDDAnnotation.class));
        assertNotNull(AnnotationUtils.findAnnotation(TestDomainRepository.class, Repository.class));
    }

    /**
     * 验证<code>annotation</code>非空，<code>annotation.aggregateRoot()</code>为真。
     */
    @Test
    void domainEntityShouldKeepAggregateRootAttribute() {
        /** 聚合根属性样例。 */
        @DomainEntity(aggregateRoot = true)
        class AggregateRoot {
        }

        DomainEntity annotation = AggregateRoot.class.getAnnotation(DomainEntity.class);
        assertNotNull(annotation);
        assertTrue(annotation.aggregateRoot());
    }

    /**
     * 验证<code>annotations.size()</code> 等于 <code>10</code>，<code>loadOptional("io.ddd4j.spring.annotation.DomainEvent")</code>为空。
     */
    @Test
    void springAnnotationPackageShouldContainExpectedAnnotations() {
        Set<Class<?>> annotations = new HashSet<>(Arrays.asList(
                DomainService.class,
                DomainRepository.class,
                DomainEntity.class,
                DomainValueObject.class,
                DomainGateway.class,
                DomainAssembler.class,
                DomainConverter.class,
                ApplicationService.class,
                QueryService.class,
                CommandExecutor.class
        ));

        assertEquals(10, annotations.size());
        assertNull(loadOptional("io.ddd4j.spring.annotation.DomainEvent"));
    }
}
