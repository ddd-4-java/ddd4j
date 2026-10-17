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

import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.metadata.data.ReadCellData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 三线共同的导入错误值契约。 */
class ImportErrorContractTest {

    @Test
    void cellConversionErrorPreservesLocationValueAndMessage() {
        ReadCellData<String> cell = new ReadCellData<>("raw");
        ExcelDataConvertException exception = new ExcelDataConvertException(
                2, 3, cell, null, "cannot convert");

        ImportError error = ImportError.of(99, exception);

        assertEquals(2, error.getRowIndex());
        assertEquals(3, error.getColumnIndex());
        assertEquals(cell.toString(), error.getCellData());
        assertEquals("cannot convert", error.getMessage());
        assertEquals("ExcelDataConvertException", error.getErrorType());
        assertEquals("ImportError{row=2, col=3, type=ExcelDataConvertException, msg=cannot convert}",
                error.toString());
    }

    @Test
    void ordinaryExceptionUsesProvidedRowAndTypeAsMissingMessage() {
        ImportError error = ImportError.of(7, new NullPointerException());

        assertEquals(7, error.getRowIndex());
        assertEquals(-1, error.getColumnIndex());
        assertNull(error.getCellData());
        assertEquals("NullPointerException", error.getMessage());
        assertEquals("NullPointerException", error.getErrorType());
    }

    @Test
    void validationErrorKeepsBusinessMessage() {
        ImportError error = ImportError.ofValidation(5, "invalid email");

        assertEquals(5, error.getRowIndex());
        assertEquals(-1, error.getColumnIndex());
        assertNull(error.getCellData());
        assertEquals("invalid email", error.getMessage());
        assertEquals("ValidationFailed", error.getErrorType());
    }
}
