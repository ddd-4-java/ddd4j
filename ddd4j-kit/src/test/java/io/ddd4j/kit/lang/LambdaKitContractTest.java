package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.lang.invoke.SerializedLambda;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 LambdaKit \u4ece Lambda \u8868\u8fbe\u5f0f\u63d0\u53d6\u65b9\u6cd5\u540d\u53ca\u5b57\u6bb5\u540d\u7684\u516c\u5f00\u5951\u7ea6\u3002 */
class LambdaKitContractTest {

    /** \u9a8c\u8bc1<code>LambdaKit.resolve(name)</code> \u7b49\u4e8e <code>"name"</code>\uff0c<code>LambdaKit.resolveClass(name)</code> \u7b49\u4e8e <code>Person.class.getName()</code>\uff0c<code>serialized.getImplMethodName()</code> \u7b49\u4e8e <code>"getName"</code>\u3002 */
    @Test
    void getterReferenceResolvesPropertyAndDeclaringType() {
        SFunction<Person, String> name = Person::getName;
        assertEquals("name", LambdaKit.resolve(name));
        assertEquals(Person.class.getName(), LambdaKit.resolveClass(name));
        assertSame(Person.class, LambdaKit.resolveType(name));
        SerializedLambda serialized = LambdaKit.extractLambda(name);
        assertEquals("getName", serialized.getImplMethodName());
    }

    /** \u9a8c\u8bc1<code>LambdaKit.resolve(active)</code> \u7b49\u4e8e <code>"active"</code>\u3002 */
    @Test
    void booleanGetterReferenceResolvesProperty() {
        SFunction<Person, Boolean> active = Person::isActive;
        assertEquals("active", LambdaKit.resolve(active));
    }

    /** \u9a8c\u8bc1<code>LambdaKit.extractLambda("ordinary string")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void nonLambdaSerializableIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LambdaKit.extractLambda("ordinary string"));
    }

    /** \u63d0\u4f9b\u5e38\u89c4 getter \u7684\u65b9\u6cd5\u5f15\u7528\u89e3\u6790\u6837\u672c\u3002 */
    private static final class Person {
        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>getName</code> \u8fd4\u56de\u503c\u3002
         * @return <code>"alice"</code>
         */
        public String getName() {
            return "alice";
        }

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>isActive</code> \u8fd4\u56de\u503c\u3002
         * @return <code>true</code>
         */
        public Boolean isActive() {
            return true;
        }
    }
}
