package io.ddd4j.web.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 反射工具类。
 * <p>提供泛型父类类型参数的解析功能。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@UtilityClass
public final class ReflectKit {

    /**
     * 获取指定类的父类泛型参数类型。
     *
     * @param clazz 要解析的类
     * @param index 泛型参数索引（从 0 开始）
     * @param <T>   泛型类型
     * @return 指定索引处的泛型参数 Class
     * @throws RuntimeException 如果父类不是参数化类型或无法获取泛型参数
     */
    public <T> Class<T> getSuperClassGenericType(final Class<?> clazz, final int index) {
        Type genType = clazz.getGenericSuperclass();
        if (!(genType instanceof ParameterizedType)) {
            log.warn(String.format("Warn: %s's superclass not ParameterizedType", clazz.getSimpleName()));
            throw new RuntimeException(String.format("Warn: %s's superclass not ParameterizedType", clazz.getSimpleName()));
        } else {
            Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
            if (index < params.length && index >= 0) {
                if (!(params[index] instanceof Class)) {
                    log.warn(String.format("Warn: %s not set the actual class on superclass generic parameter", clazz.getSimpleName()));
                    throw new RuntimeException(String.format("Warn: %s's superclass not ParameterizedType", clazz.getSimpleName()));
                } else {
                    return (Class<T>) params[index];
                }
            } else {
                log.warn(String.format("Warn: Index: %s, Size of %s's Parameterized Type: %s .", index, clazz.getSimpleName(), params.length));
                throw new RuntimeException(String.format("Warn: %s's superclass not ParameterizedType", clazz.getSimpleName()));
            }
        }
    }
}
