package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.lang.invoke.SerializedLambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaKitContractTest {

    @Test
    void getterReferenceResolvesPropertyAndDeclaringType() {
        SFunction<Person, String> name = Person::getName;
        assertEquals("name", LambdaKit.resolve(name));
        assertEquals(Person.class.getName(), LambdaKit.resolveClass(name));
        assertSame(Person.class, LambdaKit.resolveType(name));
        SerializedLambda serialized = LambdaKit.extractLambda(name);
        assertEquals("getName", serialized.getImplMethodName());
    }

    @Test
    void booleanGetterReferenceResolvesProperty() {
        SFunction<Person, Boolean> active = Person::isActive;
        assertEquals("active", LambdaKit.resolve(active));
    }

    @Test
    void nonLambdaSerializableIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LambdaKit.extractLambda("ordinary string"));
    }

    private static final class Person {
        public String getName() {
            return "alice";
        }

        public Boolean isActive() {
            return true;
        }
    }
}
