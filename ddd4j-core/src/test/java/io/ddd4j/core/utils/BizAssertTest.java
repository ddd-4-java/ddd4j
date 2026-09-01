package io.ddd4j.core.utils;

import io.ddd4j.core.contract.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BizAssert} 断言行为单元测试：断言不通过应抛出 {@link ServiceException}。
 */
class BizAssertTest {

    @Test
    void assertTrue_shouldNotThrowWhenConditionTrue() {
        assertDoesNotThrow(() -> BizAssert.isTrue(true, "should not throw"));
    }

    @Test
    void assertTrue_shouldThrowServiceExceptionWhenConditionFalse() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> BizAssert.isTrue(false, 4001, "condition failed"));
        assertEquals(4001, ex.getCode());
        assertEquals("condition failed", ex.getMessage());
    }

    @Test
    void notNull_shouldThrowWhenValueIsNull() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> BizAssert.notNull(null, "value required"));
        assertEquals("value required", ex.getMessage());
    }

    @Test
    void notEmpty_shouldThrowWhenCollectionEmpty() {
        List<String> empty = Collections.emptyList();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> BizAssert.hasValue(empty, "collection must not be empty"));
        assertEquals("collection must not be empty", ex.getMessage());
    }

    @Test
    void assertEquals_shouldThrowWhenNotEqual() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> BizAssert.equals("a", "b", "not equal"));
        assertEquals("not equal", ex.getMessage());
    }

    @Test
    void assertEquals_shouldNotThrowWhenEqual() {
        assertDoesNotThrow(() -> BizAssert.equals("a", "a"));
    }
}
