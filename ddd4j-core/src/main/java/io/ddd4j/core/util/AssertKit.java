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
package io.ddd4j.core.util;

import io.ddd4j.core.api.IR;
import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.exception.BizRuntimeException;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 业务断言类，断言不通过将抛出 {@link BizRuntimeException}。
 *
 * <p>纯 Java 实现（零框架依赖），所有断言方法均支持：
 * <ul>
 *   <li>无参重载：抛出默认错误信息</li>
 *   <li>带 {@code String ifXxx, Object... params} 重载：抛出 i18n 格式化错误信息</li>
 *   <li>带 {@code Integer code, String ifXxx, Object... params} 重载：抛出指定错误码 + i18n 错误信息</li>
 * </ul>
 *
 * <p>从 {@code io.ddd4j.spring.util.BizAssert}（Spring 模块误置）下沉至 {@code ddd4j-core}，
 * 消除业务代码对 Spring 模块的强依赖，使 Javalin / Quarkus 消费方可用。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@UtilityClass
public class AssertKit {
    /**
     * 断言是否为真，如果为 {@code false} 抛出给定的异常<br>
     *
     * <pre class="code">
     * Assert.isTrue(i &gt; 0, BizRuntimeException::new);
     * </pre>
     *
     * @param <X>        异常类型
     * @param expect     布尔值
     * @param falseThrow 指定断言不通过时抛出的异常
     * @throws X if expect is {@code false}
     */
    public <X extends Throwable> void isTrue(Boolean expect, Supplier<? extends X> falseThrow) throws X {
        if (!expect) {
            throw falseThrow.get();
        }
    }

    /**
     * 要求条件为真；否则抛出业务异常。
     *
     * @param expect 待断言条件
     * @param code 断言失败时的业务错误码
     * @param falseThrow 断言失败时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void isTrue(Boolean expect, Integer code, String falseThrow, Object... params) {
        if (!expect) {
            throw new BizRuntimeException(code, falseThrow, params);
        }
    }

    /**
     * 要求条件为真；否则抛出业务异常。
     *
     * @param expect 待断言条件
     * @param ifFalse 断言失败时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void isTrue(Boolean expect, String ifFalse, Object... params) {
        if (!expect) {
            throw new BizRuntimeException(ifFalse, params);
        }
    }

    /**
     * 要求两个对象相等；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifNotEquals 不相等时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void equals(Object a, Object b, Integer code, String ifNotEquals, Object... params) {
        if (!Objects.equals(a, b)) {
            throw new BizRuntimeException(code, ifNotEquals, params);
        }
    }

    /**
     * 要求两个对象相等；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifNotEquals 不相等时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void equals(Object a, Object b, String ifNotEquals, Object... params) {
        if (!Objects.equals(a, b)) {
            throw new BizRuntimeException(ifNotEquals, params);
        }
    }

    /**
     * 要求两个对象相等；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void equals(Object a, Object b) {
        if (!Objects.equals(a, b)) {
            throw new BizRuntimeException("a must equals with b");
        }
    }

    /**
     * 要求两个对象不相等；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifEquals 相等时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void notEquals(Object a, Object b, Integer code, String ifEquals, Object... params) {
        if (Objects.equals(a, b)) {
            throw new BizRuntimeException(code, ifEquals, params);
        }
    }

    /**
     * 要求两个对象不相等；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifEquals 相等时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void notEquals(Object a, Object b, String ifEquals, Object... params) {
        if (Objects.equals(a, b)) {
            throw new BizRuntimeException(ifEquals, params);
        }
    }

    /**
     * 要求两个对象不相等；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void notEquals(Object a, Object b) {
        if (Objects.equals(a, b)) {
            throw new BizRuntimeException("a must not equals with b");
        }
    }

    /**
     * 要求集合非空且包含指定元素；否则抛出业务异常。
     *
     * @param collection 待检查集合
     * @param element 待检查元素
     * @param code 断言失败时的业务错误码
     * @param ifNotContains 不包含元素时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void contains(Collection collection, Object element, Integer code, String ifNotContains, Object... params) {
        if (Objects.isNull(collection) || !collection.contains(element)) {
            throw new BizRuntimeException(code, ifNotContains, params);
        }
    }

    /**
     * 要求集合非空且包含指定元素；否则抛出业务异常。
     *
     * @param collection 待检查集合
     * @param element 待检查元素
     * @param ifNotContains 不包含元素时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void contains(Collection collection, Object element, String ifNotContains, Object... params) {
        if (Objects.isNull(collection) || !collection.contains(element)) {
            throw new BizRuntimeException(ifNotContains, params);
        }
    }

    /**
     * 要求集合非空且包含指定元素；否则抛出业务异常。
     *
     * @param collection 待检查集合
     * @param element 待检查元素
     * @throws BizRuntimeException 断言不成立时
     */
    public void contains(Collection collection, Object element) {
        if (Objects.isNull(collection) || !collection.contains(element)) {
            throw new BizRuntimeException("collection must contains the element");
        }
    }

    /**
     * 要求集合为空或不包含指定元素；否则抛出业务异常。
     *
     * @param collection 待检查集合
     * @param element 待检查元素
     * @param code 断言失败时的业务错误码
     * @param ifContains 包含元素时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void notContains(Collection collection, Object element, Integer code, String ifContains, Object... params) {
        if (Objects.nonNull(collection) && collection.contains(element)) {
            throw new BizRuntimeException(code, ifContains, params);
        }
    }

    /**
     * 要求集合为空或不包含指定元素；否则抛出业务异常。
     *
     * @param collection 待检查集合
     * @param element 待检查元素
     * @param ifContains 包含元素时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void notContains(Collection collection, Object element, String ifContains, Object... params) {
        if (Objects.nonNull(collection) && collection.contains(element)) {
            throw new BizRuntimeException(ifContains, params);
        }
    }

    /**
     * 要求集合为空或不包含指定元素；否则抛出业务异常。
     *
     * @param collection 待检查集合
     * @param element 待检查元素
     * @throws BizRuntimeException 断言不成立时
     */
    public void notContains(Collection collection, Object element) {
        if (Objects.nonNull(collection) && collection.contains(element)) {
            throw new BizRuntimeException("collection must contains the element");
        }
    }

    /**
     * 要求第一个时间非空且晚于第二个时间；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifBefore 时间顺序不满足时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void after(LocalDateTime a, LocalDateTime b, Integer code, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(code, ifBefore, params);
        }
    }

    /**
     * 要求第一个时间非空且晚于第二个时间；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifBefore 时间顺序不满足时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void after(LocalDateTime a, LocalDateTime b, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(ifBefore, params);
        }
    }

    /**
     * 要求第一个时间非空且晚于第二个时间；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void after(LocalDateTime a, LocalDateTime b) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException("a must after b");
        }
    }

    /**
     * 要求第一个时间非空且晚于第二个时间；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifBefore 时间顺序不满足时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void after(LocalDate a, LocalDate b, Integer code, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(code, ifBefore, params);
        }
    }

    /**
     * 要求第一个时间非空且晚于第二个时间；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifBefore 时间顺序不满足时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void after(LocalDate a, LocalDate b, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(ifBefore, params);
        }
    }

    /**
     * 要求第一个时间非空且晚于第二个时间；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void after(LocalDate a, LocalDate b) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException("a must after b");
        }
    }

    /**
     * 要求第一个数非空且大于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifLessEquals 不满足大于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void gt(Integer a, Integer b, Integer code, String ifLessEquals, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a <= b) {
            throw new BizRuntimeException(code, ifLessEquals, params);
        }
    }

    /**
     * 要求第一个数非空且大于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifLessEquals 不满足大于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void gt(Integer a, Integer b, String ifLessEquals, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a <= b) {
            throw new BizRuntimeException(ifLessEquals, params);
        }
    }

    /**
     * 要求第一个数非空且大于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void gt(Integer a, Integer b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a <= b) {
            throw new BizRuntimeException("a must > b");
        }
    }

    /**
     * 要求第一个数非空且大于或等于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifLess 不满足大于或等于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void ge(Integer a, Integer b, Integer code, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a < b) {
            throw new BizRuntimeException(code, ifLess, params);
        }
    }

    /**
     * 要求第一个数非空且大于或等于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifLess 不满足大于或等于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void ge(Integer a, Integer b, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a < b) {
            throw new BizRuntimeException(ifLess, params);
        }
    }

    /**
     * 要求第一个数非空且大于或等于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void ge(Integer a, Integer b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a < b) {
            throw new BizRuntimeException("a must >= b");
        }
    }

    /**
     * 要求第一个数非空且大于或等于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifLess 不满足大于或等于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void ge(BigDecimal a, BigDecimal b, Integer code, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new BizRuntimeException(code, ifLess, params);
        }
    }

    /**
     * 要求第一个数非空且大于或等于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifLess 不满足大于或等于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void ge(BigDecimal a, BigDecimal b, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new BizRuntimeException(ifLess, params);
        }
    }

    /**
     * 要求第一个数非空且大于或等于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void ge(BigDecimal a, BigDecimal b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new BizRuntimeException("a must >= b");
        }
    }

    /**
     * 要求第一个数非空且大于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param code 断言失败时的业务错误码
     * @param ifLess 不满足大于或等于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void gt(BigDecimal a, BigDecimal b, Integer code, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new BizRuntimeException(code, ifLess, params);
        }
    }

    /**
     * 要求第一个数非空且大于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @param ifLess 不满足大于或等于关系时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void gt(BigDecimal a, BigDecimal b, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new BizRuntimeException(ifLess, params);
        }
    }

    /**
     * 要求第一个数非空且大于第二个数；否则抛出业务异常。
     *
     * @param a 第一个比较值
     * @param b 第二个比较值
     * @throws BizRuntimeException 断言不成立时
     */
    public void gt(BigDecimal a, BigDecimal b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new BizRuntimeException("a must > b");
        }
    }

    /**
     * 要求对象非空，并返回原对象。
     *
     * @param <T> 响应数据或原对象的类型
     * @param dontNull 待检查对象
     * @param code 断言失败时的业务错误码
     * @param ifNull 对象为 null 时的错误消息
     * @param params 用于格式化错误消息的参数
     * @return 经非空校验的原对象
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T notNull(T dontNull, Integer code, String ifNull, Object... params) {
        if (Objects.isNull(dontNull)) {
            throw new BizRuntimeException(code, ifNull, params);
        }
        return dontNull;
    }

    /**
     * 要求对象非空，并返回原对象。
     *
     * @param <T> 响应数据或原对象的类型
     * @param dontNull 待检查对象
     * @param ifNull 对象为 null 时的错误消息
     * @param params 用于格式化错误消息的参数
     * @return 经非空校验的原对象
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T notNull(T dontNull, String ifNull, Object... params) {
        if (Objects.isNull(dontNull)) {
            throw new BizRuntimeException(ifNull, params);
        }
        return dontNull;
    }

    /**
     * 要求对象非空，并返回原对象。
     *
     * @param <T> 响应数据或原对象的类型
     * @param dontNull 待检查对象
     * @return 经非空校验的原对象
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T notNull(T dontNull) {
        if (Objects.isNull(dontNull)) {
            throw new BizRuntimeException("object must not be null");
        }
        return dontNull;
    }

    /**
     * 要求对象为 null；否则抛出业务异常。
     *
     * @param nullVal 待检查对象
     * @param code 断言失败时的业务错误码
     * @param ifNotNull 对象非空时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void isNull(Object nullVal, Integer code, String ifNotNull, Object... params) {
        if (Objects.nonNull(nullVal)) {
            throw new BizRuntimeException(code, ifNotNull, params);
        }
    }

    /**
     * 要求对象为 null；否则抛出业务异常。
     *
     * @param nullVal 待检查对象
     * @param ifNotNull 对象非空时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void isNull(Object nullVal, String ifNotNull, Object... params) {
        if (Objects.nonNull(nullVal)) {
            throw new BizRuntimeException(ifNotNull, params);
        }
    }

    /**
     * 要求对象为 null；否则抛出业务异常。
     *
     * @param nullVal 待检查对象
     * @throws BizRuntimeException 断言不成立时
     */
    public void isNull(Object nullVal) {
        if (Objects.nonNull(nullVal)) {
            throw new BizRuntimeException("object must be null");
        }
    }

    /**
     * 要求对象有值；null 或空内容触发业务异常。
     *
     * @param object 待检查对象
     * @param code 断言失败时的业务错误码
     * @param missingValue 对象无值时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void hasValue(Object object, Integer code, String missingValue, Object... params) {
        if (Objects.isNull(object) || isEmpty(object)) {
            throw new BizRuntimeException(code, missingValue, params);
        }
    }

    /**
     * 要求对象有值；null 或空内容触发业务异常。
     *
     * @param object 待检查对象
     * @param ifEmpty 对象无值时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void hasValue(Object object, String ifEmpty, Object... params) {
        hasValue(object, ApiCode.FAIL.getCode(), ifEmpty, params);
    }

    /**
     * 要求对象有值；null 或空内容触发业务异常。
     *
     * @param dontEmpty 待检查对象
     * @throws BizRuntimeException 断言不成立时
     */
    public void hasValue(Object dontEmpty) {
        hasValue(dontEmpty, ApiCode.FAIL.getCode(), "this object missing value");
    }

    /**
     * 要求对象为 null 或空内容；否则抛出业务异常。
     *
     * @param object 待检查对象
     * @param code 断言失败时的业务错误码
     * @param hasValue 对象有值时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void mustEmpty(Object object, Integer code, String hasValue, Object... params) {
        if (Objects.nonNull(object) && !isEmpty(object)) {
            throw new BizRuntimeException(code, hasValue, params);
        }
    }

    /**
     * 要求对象为 null 或空内容；否则抛出业务异常。
     *
     * @param object 待检查对象
     * @param hasValue 对象有值时的错误消息
     * @param params 用于格式化错误消息的参数
     * @throws BizRuntimeException 断言不成立时
     */
    public void mustEmpty(Object object, String hasValue, Object... params) {
        mustEmpty(object, ApiCode.FAIL.getCode(), hasValue, params);
    }

    /**
     * 要求对象为 null 或空内容；否则抛出业务异常。
     *
     * @param object 待检查对象
     * @throws BizRuntimeException 断言不成立时
     */
    public void mustEmpty(Object object) {
        mustEmpty(object, ApiCode.FAIL.getCode(), "this object has value");
    }

    /**
     * 要求响应非空且成功，并返回响应数据。
     *
     * @param <T> 响应数据或原对象的类型
     * @param r 待检查响应
     * @param code 断言失败时的业务错误码
     * @param ifNotOk 响应不成功时的错误消息
     * @param params 用于格式化错误消息的参数
     * @return 成功响应中的数据
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T isOk(IR r, Integer code, String ifNotOk, Object... params) {
        if (Objects.isNull(r) || !r.isOk()) {
            throw new BizRuntimeException(code, ifNotOk, params);
        }
        return r.getData();
    }

    /**
     * 要求响应非空且成功，并返回响应数据。
     *
     * @param <T> 响应数据或原对象的类型
     * @param r 待检查响应
     * @param ifNotOk 响应不成功时的错误消息
     * @param params 用于格式化错误消息的参数
     * @return 成功响应中的数据
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T isOk(IR r, String ifNotOk, Object... params) {
        return isOk(r, ApiCode.FAIL.getCode(), ifNotOk, params);
    }

    /**
     * 要求响应非空且成功，并返回响应数据。
     *
     * @param <T> 响应数据或原对象的类型
     * @param r 待检查响应
     * @return 成功响应中的数据
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T isOk(IR r) {
        return isOk(r, ApiCode.FAIL.getCode(), Objects.isNull(r) ? "this result missing value" : r.getMsg());
    }

    /**
     * 要求响应非空且成功，并返回响应数据。
     *
     * @param <T> 响应数据或原对象的类型
     * @param r 待检查响应
     * @param notOk 响应不成功时生成错误消息的回调
     * @return 成功响应中的数据
     * @throws BizRuntimeException 断言不成立时
     */
    public <T> T isOk(IR r, Supplier<String> notOk) {
        if (Objects.isNull(r) || !r.isOk()) {
            throw new BizRuntimeException(notOk.get());
        }
        return r.getData();
    }

    /**
     * 智能判空：支持 Iterable / Array / String / Map / Optional / IR 等常见类型。
     * 对于其他对象类型，默认认为非 null 的对象就是有效的。
     *
     * @param object 待判断对象
     * @return true 表示为空（无内容）
     */
    public boolean isEmpty(Object object) {
        if (object instanceof Iterable) {
            return !((Iterable<?>) object).iterator().hasNext();
        } else if (object.getClass().isArray()) {
            return Array.getLength(object) == 0;
        } else if (object instanceof String) {
            return io.ddd4j.kit.lang.StrKit.isEmpty(((String) object));
        } else if (object instanceof Map) {
            return ((Map<?, ?>) object).isEmpty();
        } else if (object instanceof Optional) {
            return !((Optional<?>) object).isPresent();
        } else if (object instanceof IR) {
            IR ir = (IR) object;
            return !ir.isOk() || Objects.isNull(ir.getData());
        }
        // 对于其他对象类型，默认认为非null的对象就是有效的。
        return false;
    }
}
