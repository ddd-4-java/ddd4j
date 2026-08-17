package io.ddd4j.guice.annotation.ddd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationFusionVerifierTest {

    @Test
    void mainVerificationPasses() {
        assertDoesNotThrow(() -> AnnotationFusionVerifier.main(new String[0]));
    }

    @Test
    void businessDomainServiceIsAnnotatedAndUsable() {
        BusinessDomainService service = new BusinessDomainService();

        assertEquals("hello", service.hello());
        assertEquals(DomainService.class,
                service.getClass().getAnnotation(DomainService.class).annotationType());
    }
}
