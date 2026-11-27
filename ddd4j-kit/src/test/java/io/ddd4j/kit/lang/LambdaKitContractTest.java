package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import java.lang.invoke.SerializedLambda;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 LambdaKit 从 Lambda 表达式提取方法名及字段名的公开契约。
 */
class LambdaKitContractTest {

    /**
     * 验证<code>LambdaKit.resolve(name)</code> 等于 <code>"name"</code>，<code>LambdaKit.resolveClass(name)</code> 等于 <code>Person.class.getName()</code>，<code>serialized.getImplMethodName()</code> 等于 <code>"getName"</code>。
     */
    @Test
    void getterReferenceResolvesPropertyAndDeclaringType() {
        SFunction<Person, String> name = Person::getName;
        assertEquals("name", LambdaKit.resolve(name));
        assertEquals(Person.class.getName(), LambdaKit.resolveClass(name));
        assertSame(Person.class, LambdaKit.resolveType(name));
        SerializedLambda serialized = LambdaKit.extractLambda(name);
        assertEquals("getName", serialized.getImplMethodName());
    }

    /**
     * 验证<code>LambdaKit.resolve(active)</code> 等于 <code>"active"</code>。
     */
    @Test
    void booleanGetterReferenceResolvesProperty() {
        SFunction<Person, Boolean> active = Person::isActive;
        assertEquals("active", LambdaKit.resolve(active));
    }

    /**
     * 验证<code>LambdaKit.extractLambda("ordinary string")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void nonLambdaSerializableIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LambdaKit.extractLambda("ordinary string"));
    }

    /**
     * 提供常规 getter 的方法引用解析样本。
     */
    private static final class Person {
        /**
         * 为当前用例提供固定的 <code>getName</code> 返回值。
         *
         * @return <code>"alice"</code>
         */
        public String getName() {
            return "alice";
        }

        /**
         * 为当前用例提供固定的 <code>isActive</code> 返回值。
         *
         * @return <code>true</code>
         */
        public Boolean isActive() {
            return true;
        }
    }
}
