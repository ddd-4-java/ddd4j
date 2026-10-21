package io.ddd4j.data.cqrs.quarkus;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarkusCommandBusPlatformContractTest {

    @Test
    void declaresTheCdiNamespaceAndProxyConstructorsForThisJdk() throws Exception {
        String namespace = "1.8".equals(System.getProperty("java.specification.version"))
                ? "javax." : "jakarta.";
        Class<?> bus = QuarkusCommandBus.class;

        assertFalse(Modifier.isFinal(bus.getModifiers()));
        assertTrue(hasAnnotation(bus, namespace + "enterprise.context.ApplicationScoped"));
        assertTrue(hasAnnotation(bus, namespace + "enterprise.context.control.ActivateRequestContext"));
        Constructor<?> injected = bus.getDeclaredConstructor(
                Class.forName(namespace + "enterprise.inject.Instance"));
        assertTrue(Modifier.isPublic(injected.getModifiers()));
        assertTrue(hasAnnotation(injected, namespace + "inject.Inject"));
        assertTrue(Modifier.isProtected(bus.getDeclaredConstructor().getModifiers()));
        assertEquals(DefaultCommandBus.class,
                bus.getMethod("execute", Command.class).getDeclaringClass());
    }

    private static boolean hasAnnotation(java.lang.reflect.AnnotatedElement element, String typeName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (typeName.equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}
