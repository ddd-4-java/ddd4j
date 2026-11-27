package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 DDD \u6ce8\u89e3\u7684 Spring \u7ec4\u5408\u6ce8\u89e3\u53ca\u805a\u5408\u6839\u6807\u8bb0\u5951\u7ea6\u3002 */
@DisplayName("ddd4j-runtime-spring DDD 注解与 Spring 元注解融合验收")
class SpringDddAnnotationFusionTest {

    /**
     * \u6838\u5bf9<code>annotationType.getAnnotation(DDDAnnotation.class)</code>\u975e\u7a7a\uff0c<code>annotationType.getAnnotation(springAnnotationType)</code>\u975e\u7a7a\u3002
     * @param annotationType \u4f20\u7ed9 <code>assertNotNull()</code> \u7684 <code>Class&lt;? extends Annotation&gt;</code> \u8f93\u5165
     * @param springAnnotationType \u4f20\u7ed9 <code>assertNotNull()</code> \u7684 <code>Class&lt;? extends Annotation&gt;</code> \u8f93\u5165
     */
    private static void assertMetaAnnotations(Class<? extends Annotation> annotationType,
                                              Class<? extends Annotation> springAnnotationType) {
        assertNotNull(annotationType.getAnnotation(DDDAnnotation.class));
        assertNotNull(annotationType.getAnnotation(springAnnotationType));
    }

    /**
     * \u8c03\u7528 <code>Class.forName()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param className \u4f20\u7ed9 <code>Class.forName()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>Class.forName(className)</code>\u3001<code>null</code>
     */
    private static Class<?> loadOptional(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    /** \u9a8c\u8bc1 DDD \u7ec4\u4ef6\u6ce8\u89e3\u516c\u5f00\u76f8\u5e94\u7684 Spring \u5143\u6ce8\u89e3\u3002 */
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

    /** \u9a8c\u8bc1<code>AnnotationUtils.findAnnotation(TestDomainService.class, Service.class)</code>\u975e\u7a7a\uff0c<code>AnnotationUtils.findAnnotation(TestDomainService.class, DDDAnnotation.class)</code>\u975e\u7a7a\uff0c<code>AnnotationUtils.findAnnotation(TestDomainRepository.class, Repository.class)</code>\u975e\u7a7a\u3002 */
    @Test
    void springShouldResolveComposedAnnotations() {
        /** \u9886\u57df\u670d\u52a1\u7ec4\u5408\u6ce8\u89e3\u6837\u4f8b\u3002 */
        @DomainService
        class TestDomainService {
        }

        /** \u9886\u57df\u4ed3\u50a8\u7ec4\u5408\u6ce8\u89e3\u6837\u4f8b\u3002 */
        @DomainRepository
        class TestDomainRepository {
        }

        assertNotNull(AnnotationUtils.findAnnotation(TestDomainService.class, Service.class));
        assertNotNull(AnnotationUtils.findAnnotation(TestDomainService.class, DDDAnnotation.class));
        assertNotNull(AnnotationUtils.findAnnotation(TestDomainRepository.class, Repository.class));
    }

    /** \u9a8c\u8bc1<code>annotation</code>\u975e\u7a7a\uff0c<code>annotation.aggregateRoot()</code>\u4e3a\u771f\u3002 */
    @Test
    void domainEntityShouldKeepAggregateRootAttribute() {
        /** \u805a\u5408\u6839\u5c5e\u6027\u6837\u4f8b\u3002 */
        @DomainEntity(aggregateRoot = true)
        class AggregateRoot {
        }

        DomainEntity annotation = AggregateRoot.class.getAnnotation(DomainEntity.class);
        assertNotNull(annotation);
        assertTrue(annotation.aggregateRoot());
    }

    /** \u9a8c\u8bc1<code>annotations.size()</code> \u7b49\u4e8e <code>10</code>\uff0c<code>loadOptional("io.ddd4j.spring.annotation.DomainEvent")</code>\u4e3a\u7a7a\u3002 */
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
        assertNull(loadOptional("io.ddd4j.spring.annotation.DomainEvent"));
    }
}
