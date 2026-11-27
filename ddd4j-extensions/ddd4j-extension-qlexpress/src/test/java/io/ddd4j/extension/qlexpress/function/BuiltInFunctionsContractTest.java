package io.ddd4j.extension.qlexpress.function;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltInFunctionsContractTest {

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
