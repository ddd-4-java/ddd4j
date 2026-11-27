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

/** \u901a\u8fc7 <code>detector.detect()</code>\u3001<code>FileValidationPolicy.builder()</code> \u9a8c\u8bc1 <code>TikaFileTypeDetectorIntegration</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class TikaFileTypeDetectorIntegrationTest {

    /** \u5f53\u524d\u88ab\u9a8c\u8bc1\u7684\u68c0\u6d4b\u7ed3\u679c\u6765\u6e90\u3002 */
    private final TikaFileTypeDetector detector = new TikaFileTypeDetector();

    /**
     * \u9a8c\u8bc1<code>detector.detect(file)</code> \u7b49\u4e8e <code>Optional.of(new DetectedFileType("pdf", "application/pdf"))</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
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

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>FileValidationPolicy.builder()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void truncatedPdfDoesNotPassStrictValidation() throws Exception {
        byte[] partial = "%PD".getBytes(StandardCharsets.US_ASCII);
        DefaultUploadFile file = new DefaultUploadFile("file", "report.pdf", "application/pdf",
                partial.length, () -> new ByteArrayInputStream(partial));

        assertFalse(new FileValidationService().validate(file,
                FileValidationPolicy.builder().allowedExtensions("pdf").build()).isValid());
    }
}
