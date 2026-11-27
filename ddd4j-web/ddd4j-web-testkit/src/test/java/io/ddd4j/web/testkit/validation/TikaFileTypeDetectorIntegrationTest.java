package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.file.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 通过 <code>detector.detect()</code>、<code>FileValidationPolicy.builder()</code> 验证 <code>TikaFileTypeDetectorIntegration</code> 的运行行为。
 */
class TikaFileTypeDetectorIntegrationTest {

    /**
     * 当前被验证的检测结果来源。
     */
    private final TikaFileTypeDetector detector = new TikaFileTypeDetector();

    /**
     * 验证<code>detector.detect(file)</code> 等于 <code>Optional.of(new DetectedFileType("pdf", "application/pdf"))</code>。
     *
     * @throws Exception 准备或读取夹具文件时失败
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
     * 在测试框架中执行 <code>FileValidationPolicy.builder()</code>，验证该场景。
     *
     * @throws Exception 准备或读取夹具文件时失败
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
