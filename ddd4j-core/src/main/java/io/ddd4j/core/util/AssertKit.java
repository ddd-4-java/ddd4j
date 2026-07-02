package io.ddd4j.core.util;

import io.ddd4j.core.api.IR;
import io.ddd4j.core.api.ResultCode;
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

    public void isTrue(Boolean expect, Integer code, String falseThrow, Object... params) {
        if (!expect) {
            throw new BizRuntimeException(code, falseThrow, params);
        }
    }

    public void isTrue(Boolean expect, String ifFalse, Object... params) {
        if (!expect) {
            throw new BizRuntimeException(ifFalse, params);
        }
    }

    public void equals(Object a, Object b, Integer code, String ifNotEquals, Object... params) {
        if (!Objects.equals(a, b)) {
            throw new BizRuntimeException(code, ifNotEquals, params);
        }
    }

    public void equals(Object a, Object b, String ifNotEquals, Object... params) {
        if (!Objects.equals(a, b)) {
            throw new BizRuntimeException(ifNotEquals, params);
        }
    }

    public void equals(Object a, Object b) {
        if (!Objects.equals(a, b)) {
            throw new BizRuntimeException("a must equals with b");
        }
    }

    public void notEquals(Object a, Object b, Integer code, String ifEquals, Object... params) {
        if (Objects.equals(a, b)) {
            throw new BizRuntimeException(code, ifEquals, params);
        }
    }

    public void notEquals(Object a, Object b, String ifEquals, Object... params) {
        if (Objects.equals(a, b)) {
            throw new BizRuntimeException(ifEquals, params);
        }
    }

    public void notEquals(Object a, Object b) {
        if (Objects.equals(a, b)) {
            throw new BizRuntimeException("a must not equals with b");
        }
    }

    public void contains(Collection collection, Object element, Integer code, String ifNotContains, Object... params) {
        if (Objects.isNull(collection) || !collection.contains(element)) {
            throw new BizRuntimeException(code, ifNotContains, params);
        }
    }

    public void contains(Collection collection, Object element, String ifNotContains, Object... params) {
        if (Objects.isNull(collection) || !collection.contains(element)) {
            throw new BizRuntimeException(ifNotContains, params);
        }
    }

    public void contains(Collection collection, Object element) {
        if (Objects.isNull(collection) || !collection.contains(element)) {
            throw new BizRuntimeException("collection must contains the element");
        }
    }

    public void notContains(Collection collection, Object element, Integer code, String ifContains, Object... params) {
        if (Objects.nonNull(collection) && collection.contains(element)) {
            throw new BizRuntimeException(code, ifContains, params);
        }
    }

    public void notContains(Collection collection, Object element, String ifContains, Object... params) {
        if (Objects.nonNull(collection) && collection.contains(element)) {
            throw new BizRuntimeException(ifContains, params);
        }
    }

    public void notContains(Collection collection, Object element) {
        if (Objects.nonNull(collection) && collection.contains(element)) {
            throw new BizRuntimeException("collection must contains the element");
        }
    }

    public void after(LocalDateTime a, LocalDateTime b, Integer code, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(code, ifBefore, params);
        }
    }

    public void after(LocalDateTime a, LocalDateTime b, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(ifBefore, params);
        }
    }

    public void after(LocalDateTime a, LocalDateTime b) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException("a must after b");
        }
    }

    public void after(LocalDate a, LocalDate b, Integer code, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(code, ifBefore, params);
        }
    }

    public void after(LocalDate a, LocalDate b, String ifBefore, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException(ifBefore, params);
        }
    }

    public void after(LocalDate a, LocalDate b) {
        if (Objects.isNull(a) || Objects.isNull(b) || !a.isAfter(b)) {
            throw new BizRuntimeException("a must after b");
        }
    }

    public void gt(Integer a, Integer b, Integer code, String ifLessEquals, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a <= b) {
            throw new BizRuntimeException(code, ifLessEquals, params);
        }
    }

    public void gt(Integer a, Integer b, String ifLessEquals, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a <= b) {
            throw new BizRuntimeException(ifLessEquals, params);
        }
    }

    public void gt(Integer a, Integer b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a <= b) {
            throw new BizRuntimeException("a must > b");
        }
    }

    public void ge(Integer a, Integer b, Integer code, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a < b) {
            throw new BizRuntimeException(code, ifLess, params);
        }
    }

    public void ge(Integer a, Integer b, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a < b) {
            throw new BizRuntimeException(ifLess, params);
        }
    }

    public void ge(Integer a, Integer b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a < b) {
            throw new BizRuntimeException("a must >= b");
        }
    }

    public void ge(BigDecimal a, BigDecimal b, Integer code, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new BizRuntimeException(code, ifLess, params);
        }
    }

    public void ge(BigDecimal a, BigDecimal b, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new BizRuntimeException(ifLess, params);
        }
    }

    public void ge(BigDecimal a, BigDecimal b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new BizRuntimeException("a must >= b");
        }
    }

    public void gt(BigDecimal a, BigDecimal b, Integer code, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new BizRuntimeException(code, ifLess, params);
        }
    }

    public void gt(BigDecimal a, BigDecimal b, String ifLess, Object... params) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new BizRuntimeException(ifLess, params);
        }
    }

    public void gt(BigDecimal a, BigDecimal b) {
        if (Objects.isNull(a) || Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new BizRuntimeException("a must > b");
        }
    }

    public <T> T notNull(T dontNull, Integer code, String ifNull, Object... params) {
        if (Objects.isNull(dontNull)) {
            throw new BizRuntimeException(code, ifNull, params);
        }
        return dontNull;
    }

    public <T> T notNull(T dontNull, String ifNull, Object... params) {
        if (Objects.isNull(dontNull)) {
            throw new BizRuntimeException(ifNull, params);
        }
        return dontNull;
    }

    public <T> T notNull(T dontNull) {
        if (Objects.isNull(dontNull)) {
            throw new BizRuntimeException("object must not be null");
        }
        return dontNull;
    }

    public void isNull(Object nullVal, Integer code, String ifNotNull, Object... params) {
        if (Objects.nonNull(nullVal)) {
            throw new BizRuntimeException(code, ifNotNull, params);
        }
    }

    public void isNull(Object nullVal, String ifNotNull, Object... params) {
        if (Objects.nonNull(nullVal)) {
            throw new BizRuntimeException(ifNotNull, params);
        }
    }

    public void isNull(Object nullVal) {
        if (Objects.nonNull(nullVal)) {
            throw new BizRuntimeException("object must be null");
        }
    }

    public void hasValue(Object object, Integer code, String missingValue, Object... params) {
        if (Objects.isNull(object) || isEmpty(object)) {
            throw new BizRuntimeException(code, missingValue, params);
        }
    }

    public void hasValue(Object object, String ifEmpty, Object... params) {
        hasValue(object, ResultCode.FAIL.getCode(), ifEmpty, params);
    }

    public void hasValue(Object dontEmpty) {
        hasValue(dontEmpty, ResultCode.FAIL.getCode(), "this object missing value");
    }

    public void mustEmpty(Object object, Integer code, String hasValue, Object... params) {
        if (Objects.nonNull(object) && !isEmpty(object)) {
            throw new BizRuntimeException(code, hasValue, params);
        }
    }

    public void mustEmpty(Object object, String hasValue, Object... params) {
        mustEmpty(object, ResultCode.FAIL.getCode(), hasValue, params);
    }

    public void mustEmpty(Object object) {
        mustEmpty(object, ResultCode.FAIL.getCode(), "this object has value");
    }

    public <T> T isOk(IR r, Integer code, String ifNotOk, Object... params) {
        if (Objects.isNull(r) || !r.isOk()) {
            throw new BizRuntimeException(code, ifNotOk, params);
        }
        return r.getData();
    }

    public <T> T isOk(IR r, String ifNotOk, Object... params) {
        return isOk(r, ResultCode.FAIL.getCode(), ifNotOk, params);
    }

    public <T> T isOk(IR r) {
        return isOk(r, ResultCode.FAIL.getCode(), Objects.isNull(r) ? "this result missing value" : r.getMsg());
    }

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
