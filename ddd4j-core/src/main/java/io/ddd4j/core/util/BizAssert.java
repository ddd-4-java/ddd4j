package io.ddd4j.core.util;

import io.ddd4j.core.contract.IR;
import io.ddd4j.core.contract.enums.ResultCode;
import io.ddd4j.core.contract.exception.ServiceException;
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
 * 业务断言类，断言不通过将抛出 {@link ServiceException}。
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
public class BizAssert {
    /**
     * 断言是否为真，如果为 {@code false} 抛出给定的异常<br>
     *
     * <pre class="code">
     * Assert.isTrue(i &gt; 0, ServiceException::new);
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
            throw new ServiceException(code, falseThrow, params);
        }
    }

    public void isTrue(Boolean expect, String ifFalse, Object... params) {
        if (!expect) {
            throw new ServiceException(ifFalse, params);
        }
    }

    public void equals(Object a, Object b, Integer code, String ifNotEquals, Object... params) {
        if (!Objects.equals(a, b)) {
            throw new ServiceException(code, ifNotEquals, params);
        }
    }

    public void equals(Object a, Object b, String ifNotEquals, Object... params) {
        if (!Objects.equals(a, b)) {
            throw new ServiceException(ifNotEquals, params);
        }
    }

    public void equals(Object a, Object b) {
        if (!Objects.equals(a, b)) {
            throw new ServiceException("a must equals with b");
        }
    }

    public void notEquals(Object a, Object b, Integer code, String ifEquals, Object... params) {
        if (Objects.equals(a, b)) {
            throw new ServiceException(code, ifEquals, params);
        }
    }

    public void notEquals(Object a, Object b, String ifEquals, Object... params) {
        if (Objects.equals(a, b)) {
            throw new ServiceException(ifEquals, params);
        }
    }

    public void notEquals(Object a, Object b) {
        if (Objects.equals(a, b)) {
            throw new ServiceException("a must not equals with b");
        }
    }

    public void contains(Collection collection, Object element, Integer code, String ifNotContains, Object... params) {
        if (java.util.Objects.isNull(collection) || !collection.contains(element)) {
            throw new ServiceException(code, ifNotContains, params);
        }
    }

    public void contains(Collection collection, Object element, String ifNotContains, Object... params) {
        if (java.util.Objects.isNull(collection) || !collection.contains(element)) {
            throw new ServiceException(ifNotContains, params);
        }
    }

    public void contains(Collection collection, Object element) {
        if (java.util.Objects.isNull(collection) || !collection.contains(element)) {
            throw new ServiceException("collection must contains the element");
        }
    }

    public void notContains(Collection collection, Object element, Integer code, String ifContains, Object... params) {
        if (java.util.Objects.nonNull(collection) && collection.contains(element)) {
            throw new ServiceException(code, ifContains, params);
        }
    }

    public void notContains(Collection collection, Object element, String ifContains, Object... params) {
        if (java.util.Objects.nonNull(collection) && collection.contains(element)) {
            throw new ServiceException(ifContains, params);
        }
    }

    public void notContains(Collection collection, Object element) {
        if (java.util.Objects.nonNull(collection) && collection.contains(element)) {
            throw new ServiceException("collection must contains the element");
        }
    }

    public void after(LocalDateTime a, LocalDateTime b, Integer code, String ifBefore, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || !a.isAfter(b)) {
            throw new ServiceException(code, ifBefore, params);
        }
    }

    public void after(LocalDateTime a, LocalDateTime b, String ifBefore, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || !a.isAfter(b)) {
            throw new ServiceException(ifBefore, params);
        }
    }

    public void after(LocalDateTime a, LocalDateTime b) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || !a.isAfter(b)) {
            throw new ServiceException("a must after b");
        }
    }

    public void after(LocalDate a, LocalDate b, Integer code, String ifBefore, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || !a.isAfter(b)) {
            throw new ServiceException(code, ifBefore, params);
        }
    }

    public void after(LocalDate a, LocalDate b, String ifBefore, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || !a.isAfter(b)) {
            throw new ServiceException(ifBefore, params);
        }
    }

    public void after(LocalDate a, LocalDate b) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || !a.isAfter(b)) {
            throw new ServiceException("a must after b");
        }
    }

    public void gt(Integer a, Integer b, Integer code, String ifLessEquals, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a <= b) {
            throw new ServiceException(code, ifLessEquals, params);
        }
    }

    public void gt(Integer a, Integer b, String ifLessEquals, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a <= b) {
            throw new ServiceException(ifLessEquals, params);
        }
    }

    public void gt(Integer a, Integer b) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a <= b) {
            throw new ServiceException("a must > b");
        }
    }

    public void ge(Integer a, Integer b, Integer code, String ifLess, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a < b) {
            throw new ServiceException(code, ifLess, params);
        }
    }

    public void ge(Integer a, Integer b, String ifLess, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a < b) {
            throw new ServiceException(ifLess, params);
        }
    }

    public void ge(Integer a, Integer b) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a < b) {
            throw new ServiceException("a must >= b");
        }
    }

    public void ge(BigDecimal a, BigDecimal b, Integer code, String ifLess, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new ServiceException(code, ifLess, params);
        }
    }

    public void ge(BigDecimal a, BigDecimal b, String ifLess, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new ServiceException(ifLess, params);
        }
    }

    public void ge(BigDecimal a, BigDecimal b) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a.compareTo(b) < 0) {
            throw new ServiceException("a must >= b");
        }
    }

    public void gt(BigDecimal a, BigDecimal b, Integer code, String ifLess, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new ServiceException(code, ifLess, params);
        }
    }

    public void gt(BigDecimal a, BigDecimal b, String ifLess, Object... params) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new ServiceException(ifLess, params);
        }
    }

    public void gt(BigDecimal a, BigDecimal b) {
        if (java.util.Objects.isNull(a) || java.util.Objects.isNull(b) || a.compareTo(b) <= 0) {
            throw new ServiceException("a must > b");
        }
    }

    public <T> T notNull(T dontNull, Integer code, String ifNull, Object... params) {
        if (java.util.Objects.isNull(dontNull)) {
            throw new ServiceException(code, ifNull, params);
        }
        return dontNull;
    }

    public <T> T notNull(T dontNull, String ifNull, Object... params) {
        if (java.util.Objects.isNull(dontNull)) {
            throw new ServiceException(ifNull, params);
        }
        return dontNull;
    }

    public <T> T notNull(T dontNull) {
        if (java.util.Objects.isNull(dontNull)) {
            throw new ServiceException("object must not be null");
        }
        return dontNull;
    }

    public void isNull(Object nullVal, Integer code, String ifNotNull, Object... params) {
        if (java.util.Objects.nonNull(nullVal)) {
            throw new ServiceException(code, ifNotNull, params);
        }
    }

    public void isNull(Object nullVal, String ifNotNull, Object... params) {
        if (java.util.Objects.nonNull(nullVal)) {
            throw new ServiceException(ifNotNull, params);
        }
    }

    public void isNull(Object nullVal) {
        if (java.util.Objects.nonNull(nullVal)) {
            throw new ServiceException("object must be null");
        }
    }

    public void hasValue(Object object, Integer code, String missingValue, Object... params) {
        if (java.util.Objects.isNull(object) || isEmpty(object)) {
            throw new ServiceException(code, missingValue, params);
        }
    }

    public void hasValue(Object object, String ifEmpty, Object... params) {
        hasValue(object, ResultCode.FAIL.getCode(), ifEmpty, params);
    }

    public void hasValue(Object dontEmpty) {
        hasValue(dontEmpty, ResultCode.FAIL.getCode(), "this object missing value");
    }

    public void mustEmpty(Object object, Integer code, String hasValue, Object... params) {
        if (java.util.Objects.nonNull(object) && !isEmpty(object)) {
            throw new ServiceException(code, hasValue, params);
        }
    }

    public void mustEmpty(Object object, String hasValue, Object... params) {
        mustEmpty(object, ResultCode.FAIL.getCode(), hasValue, params);
    }

    public void mustEmpty(Object object) {
        mustEmpty(object, ResultCode.FAIL.getCode(), "this object has value");
    }

    public <T> T isOk(IR r, Integer code, String ifNotOk, Object... params) {
        if (java.util.Objects.isNull(r) || !r.isOk()) {
            throw new ServiceException(code, ifNotOk, params);
        }
        return r.getData();
    }

    public <T> T isOk(IR r, String ifNotOk, Object... params) {
        return isOk(r, ResultCode.FAIL.getCode(), ifNotOk, params);
    }

    public <T> T isOk(IR r) {
        return isOk(r, ResultCode.FAIL.getCode(), java.util.Objects.isNull(r) ? "this result missing value" : r.getMsg());
    }

    public <T> T isOk(IR r, Supplier<String> notOk) {
        if (java.util.Objects.isNull(r) || !r.isOk()) {
            throw new ServiceException(notOk.get());
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
            return !ir.isOk() || java.util.Objects.isNull(ir.getData());
        }
        // 对于其他对象类型，默认认为非null的对象就是有效的。
        return false;
    }
}
