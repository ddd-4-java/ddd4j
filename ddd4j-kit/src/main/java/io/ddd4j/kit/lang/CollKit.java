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

    public <T> List<T> convert(T[] array) {
        if (Objects.isNull(array) || array.length == 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    public <T> boolean isNotEmpty(T[] array) {
        return Objects.nonNull(array) && array.length != 0;
    }

    public <T> boolean isEmpty(T[] array) {
        return Objects.isNull(array) || array.length == 0;
    }

}