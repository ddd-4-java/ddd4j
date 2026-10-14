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

import cn.hutool.core.util.ArrayUtil;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数组工具类
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class ArrayKit extends ArrayUtil {

    /**
     * 将集合转换为数组。
     *
     * @param coll 源集合
     * @return 转换后的数组；集合为空时返回 null
     * @param <T> 元素类型
     */
    public <T> T[] convert(Collection<T> coll) {
        if (Objects.isNull(coll) || coll.size() == 0) {
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
     * @return 转换后的列表；数组为空时返回 null
     * @param <T> 元素类型
     */
    public <T> List<T> convert(T[] array) {
        if (isEmpty(array)) {
            return null;
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    public <T> T[] append(T[] array, T... e) {
        return ArrayUtil.append(array, e);
    }

    /**
     * 就地排序数组。
     *
     * @param array 待排序数组
     * @param <T> 元素类型
     */
    public <T> void sort(T[] array) {
        if (isEmpty(array)) {
            return;
        }
        array = CollKit.convert(CollKit.convert(array).stream().sorted().collect(Collectors.toList()));
    }

    public <T> boolean isNotEmpty(T[] array) {
        return Objects.nonNull(array) && array.length != 0;
    }

    public <T> boolean isEmpty(T[] array) {
        return Objects.isNull(array) || array.length == 0;
    }

}
