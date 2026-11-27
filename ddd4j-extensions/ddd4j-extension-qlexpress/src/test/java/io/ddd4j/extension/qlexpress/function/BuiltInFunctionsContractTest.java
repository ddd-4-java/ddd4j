package io.ddd4j.extension.qlexpress.function;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 通过 <code>BuiltInFunctions.all()</code>、<code>functions.size()</code>、<code>functions.get()</code> 验证 <code>BuiltInFunctionsContract</code> 的运行行为。
 */
class BuiltInFunctionsContractTest {

    /**
     * 验证<code>functions.size()</code> 等于 <code>4</code>，<code>functions.get(0).getClass()</code> 等于 <code>ContainsFunction.class</code>，<code>functions.set(0, new ContainsFunction())</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void all_returnsFourFunctionsInStableOrderAndCannotBeModified() {
        List<NamedQLFunction> functions = BuiltInFunctions.all();

        assertEquals(4, functions.size());
        assertEquals(ContainsFunction.class, functions.get(0).getClass());
        assertEquals(StartsWithFunction.class, functions.get(1).getClass());
        assertEquals(EndsWithFunction.class, functions.get(2).getClass());
        assertEquals(FormatDateFunction.class, functions.get(3).getClass());
        assertThrows(UnsupportedOperationException.class,
                () -> functions.set(0, new ContainsFunction()));
    }
}
