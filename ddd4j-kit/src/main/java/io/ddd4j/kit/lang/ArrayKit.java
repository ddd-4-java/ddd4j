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
     * @param <T>  元素类型
     * @return 转换后的数组；集合为空时返回 null
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
     * @param <T>   元素类型
     * @return 转换后的列表；数组为空时返回 null
     */
    public <T> List<T> convert(T[] array) {
        if (isEmpty(array)) {
            return null;
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    /**
     * \u4fdd\u7559\u539f\u6570\u7ec4\u5143\u7d20\u987a\u5e8f\u5e76\u628a\u7ed9\u5b9a\u5143\u7d20\u8ffd\u52a0\u5230\u672b\u5c3e\u3002
     * @param <T> \u6570\u7ec4\u5143\u7d20\u7c7b\u578b
     * @param array \u8981\u6269\u5145\u7684\u539f\u6570\u7ec4
     * @param e \u8981\u8ffd\u52a0\u7684\u5143\u7d20
     * @return \u5305\u542b\u539f\u59cb\u548c\u65b0\u589e\u5143\u7d20\u7684\u6570\u7ec4
     */
    public <T> T[] append(T[] array, T... e) {
        return ArrayUtil.append(array, e);
    }

    /**
     * 就地排序数组。
     *
     * @param array 待排序数组
     * @param <T>   元素类型
     */
    public <T> void sort(T[] array) {
        if (isEmpty(array)) {
            return;
        }
        array = CollKit.convert(CollKit.convert(array).stream().sorted().collect(Collectors.toList()));
    }

    /**
     * \u5224\u65ad\u6570\u7ec4\u662f\u5426\u5b58\u5728\u5e76\u542b\u6709\u5143\u7d20\u3002
     * @param <T> \u6570\u7ec4\u5143\u7d20\u7c7b\u578b
     * @param array \u5f85\u68c0\u67e5\u7684\u6570\u7ec4
     * @return \u6570\u7ec4\u5f15\u7528\u975e\u7a7a\u4e14\u957f\u5ea6\u5927\u4e8e\u96f6\u65f6\u8fd4\u56de true
     */
    public <T> boolean isNotEmpty(T[] array) {
        return Objects.nonNull(array) && array.length != 0;
    }

    /**
     * \u5224\u65ad\u6570\u7ec4\u662f\u5426\u4e3a\u7a7a\u5f15\u7528\u6216\u96f6\u957f\u5ea6\u3002
     * @param <T> \u6570\u7ec4\u5143\u7d20\u7c7b\u578b
     * @param array \u5f85\u68c0\u67e5\u7684\u6570\u7ec4
     * @return \u6570\u7ec4\u5f15\u7528\u4e3a\u7a7a\u6216\u957f\u5ea6\u4e3a\u96f6\u65f6\u8fd4\u56de true
     */
    public <T> boolean isEmpty(T[] array) {
        return Objects.isNull(array) || array.length == 0;
    }

}
