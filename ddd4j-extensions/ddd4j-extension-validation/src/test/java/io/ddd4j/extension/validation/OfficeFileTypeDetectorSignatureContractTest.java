package io.ddd4j.extension.validation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficeFileTypeDetectorSignatureContractTest {

    private final OfficeFileTypeDetector detector = new OfficeFileTypeDetector();

    @Test
    void readsSignatureAcrossShortInputStreamChunks() throws Exception {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        DefaultValidatableFile file = new DefaultValidatableFile("report.pdf", "application/pdf",
                pdf.length, () -> new ByteArrayInputStream(pdf) {
                    @Override
                    public synchronized int read(byte[] buffer, int offset, int length) {
                        return super.read(buffer, offset, Math.min(length, 1));
                    }
                });

        assertEquals(Optional.of(new DetectedFileType("pdf", "application/pdf")), detector.detect(file));
    }

    @Test
    void shortSignatureDoesNotClaimAFileType() throws Exception {
        byte[] partial = "%PD".getBytes(StandardCharsets.US_ASCII);
        DefaultValidatableFile file = new DefaultValidatableFile("report.pdf", "application/pdf",
                partial.length, () -> new ByteArrayInputStream(partial));

        assertEquals(Optional.empty(), detector.detect(file));
    }
}
