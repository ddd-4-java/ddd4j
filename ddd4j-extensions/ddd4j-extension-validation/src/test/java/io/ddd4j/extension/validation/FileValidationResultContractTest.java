package io.ddd4j.extension.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileValidationResultContractTest {

    @Test
    void valueStringIncludesFieldNamesForSuccessAndFailure() {
        assertEquals("FileValidationResult[valid=true, failure=null, detectedType=null]",
                FileValidationResult.valid(null).toString());
        assertEquals("FileValidationResult[valid=false, failure=null, detectedType=null]",
                FileValidationResult.invalid(null, null).toString());
    }
}
