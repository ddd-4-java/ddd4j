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
package io.ddd4j.extension.validation;

import java.util.Arrays;

import io.github.easy4j.validation.file.DefaultUploadFile;
import io.github.easy4j.validation.file.FileValidationService;
import io.github.easy4j.validation.file.FileValidationPolicy;
import io.github.easy4j.validation.file.FileValidationFailure;
import io.github.easy4j.validation.file.FileValidationResult;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.commons.io.IOUtils;
import java.io.InputStream;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class FileValidationServiceTest {

    private final FileValidationService service = new FileValidationService();

    @Test
    void shouldValidateSupportedDocumentSignatures() throws IOException {
        assertThat(validate("report.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)).isValid()).isTrue();
        assertThat(validate("report.docx", "application/octet-stream",
                ooxml("[Content_Types].xml", "word/document.xml")).isValid()).isTrue();
        assertThat(validate("report.xlsx", "application/octet-stream",
                ooxml("[Content_Types].xml", "xl/workbook.xml")).isValid()).isTrue();
        assertThat(validate("report.doc", "application/octet-stream", ole("WordDocument")).isValid()).isTrue();
        assertThat(validate("report.xls", "application/octet-stream", ole("Workbook")).isValid()).isTrue();
    }

    @Test
    void shouldRejectRenamedAndUnsupportedFiles() throws IOException {
        FileValidationResult renamed = validate("report.pdf", "application/pdf",
                ooxml("[Content_Types].xml", "word/document.xml"));
        FileValidationResult executable = validate("report.pdf", "application/pdf",
                new byte[]{0x4D, 0x5A, 0x00, 0x00});

        assertThat(renamed.getFailure()).isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
        assertThat(executable.getFailure()).isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
    }

    @Test
    void shouldApplyRequiredAndMaximumSizeRules() {
        FileValidationPolicy optional = policyBuilder().required(false).build();
        FileValidationPolicy oneByte = policyBuilder().maxSizeBytes(1).build();

        assertThat(service.validate(null, optional).isValid()).isTrue();
        assertThat(service.validate(file("report.pdf", "application/pdf", new byte[0]), policy()).getFailure())
                .isEqualTo(FileValidationFailure.EMPTY);
        assertThat(service.validate(file("report.pdf", "application/pdf", new byte[]{1, 2}), oneByte).getFailure())
                .isEqualTo(FileValidationFailure.SIZE_EXCEEDED);
    }

    @Test
    void shouldRejectMalformedOfficeContainer() throws IOException {
        assertThat(validate("report.docx", "application/octet-stream",
                malformedOoxml("[Content_Types].xml", "word/document.xml")).getFailure())
                .isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
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

    private DefaultUploadFile file(String fileName, String contentType, byte[] content) {
        return new DefaultUploadFile("file", fileName, contentType, content.length,
                () -> new ByteArrayInputStream(content));
    }

    private byte[] ooxml(String... entries) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (java.util.Arrays.asList(entries).contains("word/document.xml")) {
                try (XWPFDocument document = new XWPFDocument()) {
                    document.createParagraph().createRun().setText("content");
                    document.write(output);
                }
            } else {
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                    workbook.createSheet("data").createRow(0).createCell(0).setCellValue("content");
                    workbook.write(output);
                }
            }
            return output.toByteArray();
        }
    }

    private byte[] malformedOoxml(String... entries) throws IOException {
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
        if ("WordDocument".equals(entryName)) {
            try (InputStream input = Objects.requireNonNull(
                    getClass().getResourceAsStream("/validation/empty.doc"), "empty.doc")) {
                return IOUtils.toByteArray(input);
            }
        }
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("data").createRow(0).createCell(0).setCellValue("content");
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
