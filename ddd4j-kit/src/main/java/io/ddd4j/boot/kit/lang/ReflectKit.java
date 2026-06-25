package io.ddd4j.boot.kit.lang;

import cn.hutool.core.util.ReflectUtil;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 反射工具类
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Slf4j(topic = "### BASE-KIT : ReflectKit ###")
@UtilityClass
public class ReflectKit extends ReflectUtil {

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

    // 判断一个对象的所有字段是否为null
    public static boolean allFieldsNull(@NonNull Object obj) {
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        try {
            for (Field field : fields) {
                // 跳过 static 字段
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true); // 确保可以访问私有字段
                Object value = field.get(obj);
                if (value != null) {
                    return false; // 如果某个字段不为 null，则直接返回 false
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error accessing fields", e);
        }

        return true;
    }
}
