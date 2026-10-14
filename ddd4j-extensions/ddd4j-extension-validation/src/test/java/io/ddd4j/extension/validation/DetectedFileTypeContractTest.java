package io.ddd4j.extension.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectedFileTypeContractTest {

    @Test
    void valueAndBeanAccessorsShareOneContract() {
        DetectedFileType fileType = new DetectedFileType("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        DetectedFileType same = new DetectedFileType("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        DetectedFileType different = new DetectedFileType("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        assertEquals(fileType.extension(), fileType.getExtension());
        assertEquals(fileType.mimeType(), fileType.getMimeType());
        assertEquals(fileType, same);
        assertEquals(fileType.hashCode(), same.hashCode());
        assertEquals(-955135853, fileType.hashCode());
        assertNotEquals(fileType, different);
        assertEquals("DetectedFileType[extension=docx, mimeType=application/vnd.openxmlformats-officedocument.wordprocessingml.document]",
                fileType.toString());
    }

    @Test
    void jacksonRoundTripPreservesDetectedType() throws Exception {
        DetectedFileType fileType = new DetectedFileType("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(fileType);

        assertTrue(json.contains("\"extension\":\"docx\""));
        assertEquals(fileType, mapper.readValue(json, DetectedFileType.class));
    }
}
