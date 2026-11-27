package io.ddd4j.kit.lang;

import cn.hutool.core.util.ReflectUtil;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 反射工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-KIT : ReflectKit ###")
@UtilityClass
public class ReflectKit extends ReflectUtil {

    /**
     * \u83b7\u53d6\u7236\u7c7b\u6307\u5b9a\u4f4d\u7f6e\u7684\u6cdb\u578b\u7c7b\u578b\u3002
     * @param clazz \u5f85\u68c0\u67e5\u7684\u7c7b
     * @param index \u6cdb\u578b\u53c2\u6570\u4f4d\u7f6e
     * @param <T> \u6cdb\u578b\u7c7b\u578b
     * @return \u5bf9\u5e94\u6cdb\u578b\u7c7b\u578b
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

    // 判断一个对象的所有字段是否为null
    /**
     * \u5224\u65ad\u5bf9\u8c61\u7684\u6240\u6709\u5b57\u6bb5\u662f\u5426\u5747\u4e3a\u7a7a\u3002
     * @param obj \u5f85\u68c0\u67e5\u5bf9\u8c61
     * @return \u6240\u6709\u5b57\u6bb5\u5747\u4e3a\u7a7a\u65f6\u8fd4\u56de true
     */
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
                if (Objects.nonNull(value)) {
                    return false; // 如果某个字段不为 null，则直接返回 false
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error accessing fields", e);
        }

        return true;
    }
}
