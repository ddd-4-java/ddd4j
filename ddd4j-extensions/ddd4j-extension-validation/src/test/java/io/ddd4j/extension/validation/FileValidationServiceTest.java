package io.ddd4j.extension.validation;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileValidationServiceTest {

    private final FileValidationService service = new FileValidationService();

    @Test
    void shouldValidateSupportedDocumentSignatures() throws IOException {
        assertThat(validate("report.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)).valid()).isTrue();
        assertThat(validate("report.docx", "application/octet-stream",
                ooxml("[Content_Types].xml", "word/document.xml")).valid()).isTrue();
        assertThat(validate("report.xlsx", "application/octet-stream",
                ooxml("[Content_Types].xml", "xl/workbook.xml")).valid()).isTrue();
        assertThat(validate("report.doc", "application/octet-stream", ole("WordDocument")).valid()).isTrue();
        assertThat(validate("report.xls", "application/octet-stream", ole("Workbook")).valid()).isTrue();
    }

    @Test
    void shouldRejectRenamedAndUnsupportedFiles() throws IOException {
        FileValidationResult renamed = validate("report.pdf", "application/pdf",
                ooxml("[Content_Types].xml", "word/document.xml"));
        FileValidationResult executable = validate("report.pdf", "application/pdf",
                new byte[]{0x4D, 0x5A, 0x00, 0x00});

        assertThat(renamed.failure()).isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
        assertThat(executable.failure()).isEqualTo(FileValidationFailure.TYPE_UNDETECTABLE);
    }

    @Test
    void shouldApplyRequiredAndMaximumSizeRules() {
        FileValidationPolicy optional = policyBuilder().required(false).build();
        FileValidationPolicy oneByte = policyBuilder().maxSizeBytes(1).build();

        assertThat(service.validate(null, optional).valid()).isTrue();
        assertThat(service.validate(file("report.pdf", "application/pdf", new byte[0]), policy()).failure())
                .isEqualTo(FileValidationFailure.EMPTY);
        assertThat(service.validate(file("report.pdf", "application/pdf", new byte[]{1, 2}), oneByte).failure())
                .isEqualTo(FileValidationFailure.SIZE_EXCEEDED);
    }

    private FileValidationResult validate(String fileName, String contentType, byte[] content) {
        return service.validate(file(fileName, contentType, content), policy());
    }

    private FileValidationPolicy policy() {
        return policyBuilder().build();
    }

    private FileValidationPolicy.Builder policyBuilder() {
        return FileValidationPolicy.builder()
                .allowedExtensions("doc", "docx", "xls", "xlsx", "pdf")
                .allowedMimeTypes(
                        "application/msword",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/pdf")
                .maxSizeBytes(10L * 1024L * 1024L)
                .strict(true);
    }

    private DefaultValidatableFile file(String fileName, String contentType, byte[] content) {
        return new DefaultValidatableFile(fileName, contentType, content.length,
                () -> new ByteArrayInputStream(content));
    }

    private byte[] ooxml(String... entries) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (String entry : entries) {
                zipOutputStream.putNextEntry(new ZipEntry(entry));
                zipOutputStream.write("content".getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private byte[] ole(String entryName) throws IOException {
try (POIFSFileSystem fileSystem = new POIFSFileSystem())
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            fileSystem.getRoot().createDocument(entryName,
                    new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
            fileSystem.writeFilesystem(outputStream);
            return outputStream.toByteArray();
        }
    }
}
