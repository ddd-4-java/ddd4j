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

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通用类型转换函数常量
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@UtilityClass
public class FunctionKit {

    public static final Function<Object, Boolean> TO_BOOLEAN = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Boolean ? (Boolean) value : Boolean.valueOf(value.toString());
    };

    public static final Function<Object, BigDecimal> TO_BIGDECIMAL = member -> {
        if (Objects.isNull(member)) {
            return null;
        }
        return member instanceof BigDecimal ? (BigDecimal) member : new BigDecimal(member.toString());
    };

    public static final Function<Object, Byte> TO_BYTE = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Byte ? (Byte) value : Byte.valueOf(value.toString());
    };

    public static final Function<Object, Character> TO_CHARACTER = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Character ? (Character) value : Character.valueOf(value.toString().charAt(0));
    };

    public static final Function<Object, Double> TO_DOUBLE = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Double ? (Double) value : Double.valueOf(value.toString());
    };

    public static final Function<Object, Float> TO_FLOAT = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Float ? (Float) value : Float.valueOf(value.toString());
    };

    public static final Function<Object, Integer> TO_INTEGER = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Integer ? (Integer) value : Integer.valueOf(value.toString());
    };

    public static final Function<Object, Long> TO_LONG = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
    };

    public static final Function<Object, Short> TO_SHORT = value -> {
        if (Objects.isNull(value)) {
            return null;
        }
        return value instanceof Short ? (Short) value : Short.valueOf(value.toString());
    };

    public static final Function<Object, String> TO_STRING = value -> Objects.toString(value, null);

}
