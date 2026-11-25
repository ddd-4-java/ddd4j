package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.file.DefaultUploadFile;
import io.github.easy4j.validation.file.FileValidationService;
import io.github.easy4j.validation.file.FileValidationPolicy;
import io.github.easy4j.validation.file.TikaFileTypeDetector;
import io.github.easy4j.validation.file.DetectedFileType;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TikaFileTypeDetectorIntegrationTest {

    private final TikaFileTypeDetector detector = new TikaFileTypeDetector();

    @Test
    void readsSignatureAcrossShortInputStreamChunks() throws Exception {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        DefaultUploadFile file = new DefaultUploadFile("file", "report.pdf", "application/pdf",
                pdf.length, () -> new ByteArrayInputStream(pdf) {
                    @Override
                    public synchronized int read(byte[] buffer, int offset, int length) {
                        return super.read(buffer, offset, Math.min(length, 1));
                    }
                });

        assertEquals(Optional.of(new DetectedFileType("pdf", "application/pdf")), detector.detect(file));
    }

    @Test
    void truncatedPdfDoesNotPassStrictValidation() throws Exception {
        byte[] partial = "%PD".getBytes(StandardCharsets.US_ASCII);
        DefaultUploadFile file = new DefaultUploadFile("file", "report.pdf", "application/pdf",
                partial.length, () -> new ByteArrayInputStream(partial));

        assertFalse(new FileValidationService().validate(file,
                FileValidationPolicy.builder().allowedExtensions("pdf").build()).isValid());
    }
}
