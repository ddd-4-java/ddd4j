package io.ddd4j.boot.kit.lang;

import cn.hutool.core.collection.CollUtil;
import io.ddd4j.boot.core.utils.BeanKit;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 映射工具类
 *
 * @author Jensen
 * @公众号 架构师修行录
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
        if (source == null) {
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
