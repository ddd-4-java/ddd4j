package io.ddd4j.core.util;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Lambda 表达式工具类（从方法引用中解析属性名）。
 *
 * <p>通过 {@link SerializedLambda} 机制，将 {@code UserPO::getName} 这样的方法引用
 * 解析为属性名 {@code "name"}，实现编译期类型安全的列引用。
 *
 * <p>使用示例：
 * <pre>{@code
 * String property = LambdaKit.resolve(UserPO::getName);       // → "name"
 * String property = LambdaKit.resolve(UserPO::getCreateTime); // → "createTime"
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class LambdaKit {

    private LambdaKit() {
    }

    /**
     * 从方法引用中解析属性名。
     *
     * <p>将 getter 方法引用（如 {@code UserPO::getName}）转换为属性名（如 {@code "name"}）。
     * 支持标准 JavaBean getter 命名（{@code getXxx} / {@code isXxx}）。
     *
     * @param func 方法引用（如 {@code UserPO::getName}）
     * @param <T>  对象类型
     * @return 属性名（如 {@code "name"}）
     * @throws IllegalArgumentException 如果无法解析方法引用
     */
    public static <T> String resolve(SFunction<T, ?> func) {
        SerializedLambda lambda = extractLambda(func);
        String methodName = lambda.getImplMethodName();
        return methodToProperty(methodName);
    }

    /**
     * 从方法引用中解析实现类的全限定名。
     *
     * @param func 方法引用
     * @param <T>  对象类型
     * @return 实现类的全限定名
     */
    public static <T> String resolveClass(SFunction<T, ?> func) {
        SerializedLambda lambda = extractLambda(func);
        return lambda.getImplClass().replace('/', '.');
    }

    /**
     * 从方法引用中解析实现类型。
     *
     * @param func 方法引用
     * @param <T>  对象类型
     * @return 实现类型
     */
    public static <T> Class<?> resolveType(SFunction<T, ?> func) {
        Objects.requireNonNull(func, "func must not be null");
        String className = resolveClass(func);
        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader classLoader = Objects.nonNull(contextClassLoader)
                    ? contextClassLoader : LambdaKit.class.getClassLoader();
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("无法加载 Lambda 实现类型: " + className, exception);
        }
    }

    /**
     * 提取 SerializedLambda。
     */
    public static SerializedLambda extractLambda(Serializable func) {
        try {
            Method method = func.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            Object lambdaObj = method.invoke(func);
            if (lambdaObj instanceof SerializedLambda lambda) {
                return lambda;
            }
            throw new IllegalArgumentException("无法解析 Lambda 表达式，writeReplace 返回非 SerializedLambda: " + lambdaObj);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("无法解析 Lambda 表达式: " + func.getClass().getName(), e);
        }
    }

    /**
     * 将 getter 方法名转换为属性名。
     *
     * <p>支持：
     * <ul>
     *   <li>{@code getName} → {@code name}</li>
     *   <li>{@code isActive} → {@code active}</li>
     *   <li>{@code name} → {@code name}（直接属性名）</li>
     * </ul>
     *
     * @param methodName 方法名
     * @return 属性名
     */
    public static String methodToProperty(String methodName) {
        if (Objects.isNull(methodName) || methodName.isEmpty()) {
            return methodName;
        }
        // getXxx → xxx
        if (methodName.startsWith("get") && methodName.length() > 3) {
            String property = methodName.substring(3);
            return Character.toLowerCase(property.charAt(0)) + property.substring(1);
        }
        // isXxx → xxx（boolean getter）
        if (methodName.startsWith("is") && methodName.length() > 2) {
            String property = methodName.substring(2);
            return Character.toLowerCase(property.charAt(0)) + property.substring(1);
        }
        // 直接返回（可能是字段引用）
        return methodName;
    }
}
