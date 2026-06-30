package io.ddd4j.spring.util;

import cn.hutool.core.collection.CollUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 映射工具类（Spring 适配层）
 *
 * <p>依赖 Spring BeanKit 进行属性拷贝，属于 Spring 适配层。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public final class MappingKit {
    private final Map<Class, Class> BEAN_MAPPINGS = new ConcurrentHashMap<>();

    public void map(Class source, Class target) {
        BEAN_MAPPINGS.put(source, target);
    }

    public <T> Class<T> get(Class source) {
        return (Class<T>) BEAN_MAPPINGS.get(source);
    }

    public <T, S> T convert(S source) {
        if (java.util.Objects.isNull(source)) {
            return null;
        }
        Class<T> targetClass = get(source.getClass());
        return BeanKit.copy(source, targetClass);
    }

    public <T, S> List<T> convert(List<S> source) {
        if (CollUtil.isEmpty(source)) {
            return new ArrayList<>();
        }
        Class<T> targetClass = get(source.get(0).getClass());
        return BeanKit.copy(source, targetClass);
    }

}
