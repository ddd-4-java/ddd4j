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
package io.ddd4j.extension.excel.importer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线共同的导入结果防御性拷贝契约。
 */
class ImportResultValueContractTest {

    /**
     * 验证<code>result.getData().size()</code> 等于 <code>2</code>，<code>result.getData().get(0)</code> 等于 <code>"first"</code>，<code>result.getErrors().clear()</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void factoryCopiesInputsAndPreservesNullElements() {
        List<String> data = new ArrayList<>(Arrays.asList("first", null));
        List<ImportError> errors = new ArrayList<>(Arrays.asList(ImportError.ofValidation(2, "bad"), null));

        ImportResult<String> result = ImportResult.of(data, errors);
        data.set(0, "changed");
        errors.clear();

        assertEquals(2, result.getData().size());
        assertEquals("first", result.getData().get(0));
        assertNull(result.getData().get(1));
        assertEquals(2, result.getErrors().size());
        assertEquals("bad", result.getErrors().get(0).getMessage());
        assertNull(result.getErrors().get(1));
        assertEquals(4, result.getTotalCount());
        assertEquals(0.5, result.successRate(), 0.0);
        assertThrows(UnsupportedOperationException.class, () -> result.getData().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().clear());
    }

    /**
     * 验证<code>result.getTotalCount()</code> 等于 <code>0</code>，<code>result.successRate()</code> 等于 <code>0.0</code>，<code>result.hasErrors()</code> 等于 <code>false</code>。
     */
    @Test
    void emptyResultHasNoRowsOrErrors() {
        ImportResult<String> result = ImportResult.empty();

        assertEquals(0, result.getTotalCount());
        assertEquals(0.0, result.successRate(), 0.0);
        assertEquals(false, result.hasErrors());
    }
}
