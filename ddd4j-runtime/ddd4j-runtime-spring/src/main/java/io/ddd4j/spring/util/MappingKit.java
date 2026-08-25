/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.spring.util;

import cn.hutool.core.collection.CollUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /**
     * 源类型到目标类型的映射注册表
     */
    private final Map<Class, Class> BEAN_MAPPINGS = new ConcurrentHashMap<>();

    /**
     * 注册类型映射关系。
     *
     * @param source 源类型
     * @param target 目标类型
     */
    public void map(Class source, Class target) {
        BEAN_MAPPINGS.put(source, target);
    }

    public <T> Class<T> get(Class source) {
        return (Class<T>) BEAN_MAPPINGS.get(source);
    }

    public <T, S> T convert(S source) {
        if (Objects.isNull(source)) {
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
