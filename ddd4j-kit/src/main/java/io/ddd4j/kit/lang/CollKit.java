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
package io.ddd4j.kit.lang;

import cn.hutool.core.collection.CollUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.util.*;

/**
 * 集合工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class CollKit extends CollUtil {

    /**
     * 将集合转换为数组。
     *
     * @param coll 源集合
     * @return 转换后的数组；集合为空时返回 null
     * @param <T> 元素类型
     */
    public <T> T[] convert(Collection<T> coll) {
        if (Objects.isNull(coll) || coll.isEmpty()) {
            return null;
        }
        Class tClass = null;
        for (T t : coll) {
            tClass = t.getClass();
            break;
        }
        return coll.toArray((T[]) Array.newInstance(tClass, 0));
    }

    /**
     * 将数组转换为列表。
     *
     * @param array 源数组
     * @return 转换后的列表；数组为空时返回空列表
     * @param <T> 元素类型
     */
    public <T> List<T> convert(T[] array) {
        if (Objects.isNull(array) || array.length == 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * 判断数组是否非空。
     *
     * @param array 待检查数组
     * @return 数组非空且包含元素时返回 true
     * @param <T> 元素类型
     */
    public <T> boolean isNotEmpty(T[] array) {
        return Objects.nonNull(array) && array.length != 0;
    }

    /**
     * 判断数组是否为空。
     *
     * @param array 待检查数组
     * @return 数组为 null 或长度为零时返回 true
     * @param <T> 元素类型
     */
    public <T> boolean isEmpty(T[] array) {
        return Objects.isNull(array) || array.length == 0;
    }

}