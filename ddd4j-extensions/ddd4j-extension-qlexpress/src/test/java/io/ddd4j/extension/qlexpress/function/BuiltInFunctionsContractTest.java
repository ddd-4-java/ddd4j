package io.ddd4j.extension.qlexpress.function;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** \u901a\u8fc7 <code>BuiltInFunctions.all()</code>\u3001<code>functions.size()</code>\u3001<code>functions.get()</code> \u9a8c\u8bc1 <code>BuiltInFunctionsContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class BuiltInFunctionsContractTest {

    /** \u9a8c\u8bc1<code>functions.size()</code> \u7b49\u4e8e <code>4</code>\uff0c<code>functions.get(0).getClass()</code> \u7b49\u4e8e <code>ContainsFunction.class</code>\uff0c<code>functions.set(0, new ContainsFunction())</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
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
