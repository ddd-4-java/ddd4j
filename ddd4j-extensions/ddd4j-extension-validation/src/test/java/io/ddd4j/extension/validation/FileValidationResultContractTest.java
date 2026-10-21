package io.ddd4j.extension.validation;

import com.fasterxml.jackson.databind.json.JsonMapper;
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

    @Test
    void jsonRoundTripPreservesFailureAndDetectedType() throws Exception {
        FileValidationResult result = FileValidationResult.invalid(FileValidationFailure.SIGNATURE_MISMATCH,
                new DetectedFileType("pdf", "application/pdf"));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        String json = mapper.writeValueAsString(result);
        FileValidationResult restored = mapper.readValue(json, FileValidationResult.class);

        assertEquals(result, restored);
        assertEquals(3, mapper.readTree(json).size());
    }
}
