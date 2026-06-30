package io.ddd4j.spring.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ddd4j-spring DDD 注解与 Spring 元注解融合验收")
class SpringDddAnnotationFusionTest {

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

    @Test
    void springShouldResolveComposedAnnotations() {
        @DomainService
        class TestDomainService {
        }

        @DomainRepository
        class TestDomainRepository {
        }

        assertNotNull(AnnotationUtils.findAnnotation(TestDomainService.class, Service.class));
        assertNotNull(AnnotationUtils.findAnnotation(TestDomainService.class, DDDAnnotation.class));
        assertNotNull(AnnotationUtils.findAnnotation(TestDomainRepository.class, Repository.class));
    }

    @Test
    void domainEntityShouldKeepAggregateRootAttribute() {
        @DomainEntity(aggregateRoot = true)
        class AggregateRoot {
        }

        DomainEntity annotation = AggregateRoot.class.getAnnotation(DomainEntity.class);
        assertNotNull(annotation);
        assertTrue(annotation.aggregateRoot());
    }

    @Test
    void springAnnotationPackageShouldContainExpectedAnnotations() {
        Set<Class<?>> annotations = Set.of(
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
        );

        assertEquals(10, annotations.size());
        assertNull(loadOptional("io.ddd4j.spring.annotation.ddd.DomainEvent"));
    }

    private static void assertMetaAnnotations(Class<? extends Annotation> annotationType,
                                              Class<? extends Annotation> springAnnotationType) {
        assertNotNull(annotationType.getAnnotation(DDDAnnotation.class));
        assertNotNull(annotationType.getAnnotation(springAnnotationType));
    }

    private static Class<?> loadOptional(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}
