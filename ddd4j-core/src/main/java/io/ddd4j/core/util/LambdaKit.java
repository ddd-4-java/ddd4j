package io.ddd4j.core.util;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/** 从可序列化 getter 方法引用解析 JavaBean 属性。 */
public final class LambdaKit {
    private LambdaKit() { }
    public static <T> String resolve(SFunction<T, ?> function) { return methodToProperty(extractLambda(function).getImplMethodName()); }
    public static <T> Class<?> resolveType(SFunction<T, ?> function) {
        String className = extractLambda(function).getImplClass().replace('/', '.');
        try { return Class.forName(className, false, Thread.currentThread().getContextClassLoader()); }
        catch (ClassNotFoundException exception) { throw new IllegalArgumentException("Cannot load lambda implementation type: " + className, exception); }
    }
    public static SerializedLambda extractLambda(Serializable function) {
        try {
            Method method = function.getClass().getDeclaredMethod("writeReplace"); method.setAccessible(true);
            Object value = method.invoke(function);
            if (value instanceof SerializedLambda) return (SerializedLambda) value;
            throw new IllegalArgumentException("writeReplace did not return SerializedLambda");
        } catch (Exception exception) { throw new IllegalArgumentException("Cannot resolve lambda", exception); }
    }
    public static String methodToProperty(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.startsWith("get") && name.length() > 3) return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        if (name.startsWith("is") && name.length() > 2) return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        return name;
    }
}
