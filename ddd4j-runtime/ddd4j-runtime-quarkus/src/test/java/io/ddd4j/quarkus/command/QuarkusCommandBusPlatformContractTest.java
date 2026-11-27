package io.ddd4j.quarkus.command;

import io.ddd4j.core.cqrs.command.Command;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 <code>QuarkusCommandBusPlatformContract</code> 在实际装配或测试替身中的行为。 */
class QuarkusCommandBusPlatformContractTest {

    /**
     * 验证<code>Modifier.isFinal(bus.getModifiers())</code>为假，<code>hasAnnotation(bus, namespace + "enterprise.context.ApplicationScoped")</code>为真，<code>hasAnnotation(bus, namespace + "enterprise.context.control.ActivateRequestContext")</code>为真。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void declaresTheCdiNamespaceAndProxyConstructorsForThisJdk() throws Exception {
        String namespace = "1.8".equals(System.getProperty("java.specification.version"))
                ? "javax." : "jakarta.";
        Class<?> bus = QuarkusCommandBus.class;

        assertFalse(Modifier.isFinal(bus.getModifiers()));
        assertTrue(hasAnnotation(bus, namespace + "enterprise.context.ApplicationScoped"));
        assertTrue(hasAnnotation(bus, "io.quarkus.arc.DefaultBean"));
        assertTrue(hasAnnotation(bus.getDeclaredField("executors"), namespace + "inject.Inject"));
        assertEquals(Class.forName(namespace + "enterprise.inject.Instance"),
                bus.getDeclaredField("executors").getType());
        for (String entry : new String[]{"execute", "executeVoid"}) {
            assertTrue(hasAnnotation(bus.getMethod(entry, Command.class),
                    namespace + "enterprise.context.control.ActivateRequestContext"));
        }
        assertEquals(io.ddd4j.core.api.R.class, bus.getMethod("execute", Command.class).getReturnType());
    }

    /**
     * 执行 <code>has Annotation</code>，调用 <code>element.getAnnotations</code>、<code>typeName.equals</code>、<code>annotation.annotationType</code>。
     *
     * @param element 传入此操作的 <code>java.lang.reflect.AnnotatedElement</code> 类型 <code>element</code>
     * @param typeName 传入此操作的 <code>String</code> 类型 <code>typeName</code>
     * @return 按条件返回 <code>true</code>、<code>false</code>
     */
    private static boolean hasAnnotation(java.lang.reflect.AnnotatedElement element, String typeName) {
        for (Annotation annotation : element.getAnnotations()) {
            if (typeName.equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}
