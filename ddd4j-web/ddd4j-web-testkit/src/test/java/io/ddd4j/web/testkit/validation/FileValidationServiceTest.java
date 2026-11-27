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
package io.ddd4j.web.testkit.validation;

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

/** \u901a\u8fc7 <code>FileValidationResult.valid()</code>\u3001<code>FileValidationResult.invalid()</code>\u3001<code>validate()</code> \u9a8c\u8bc1 <code>FileValidationService</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class FileValidationServiceTest {

    /** \u5f53\u524d\u6d4b\u8bd5\u8c03\u7528\u6216\u9a8c\u8bc1\u751f\u547d\u5468\u671f\u7684\u5e94\u7528\u670d\u52a1\u3002 */
    private final FileValidationService service = new FileValidationService();

    /** \u9a8c\u8bc1<code>FileValidationResult.valid(null).toString()</code> \u5305\u542b <code>"valid=true"</code>\u3001<code>"failure=null"</code>\u3001<code>"detectedType=null"</code>\uff0c<code>FileValidationResult.invalid(FileValidationFailure.EMPTY, null).toString()</code> \u5305\u542b <code>"valid=false"</code>\u3001<code>"failure=EMPTY"</code>\u3001<code>"detectedType=null"</code>\u3002 */
    @Test
    void valueStringIncludesFieldNamesForSuccessAndFailure() {
        assertThat(FileValidationResult.valid(null).toString()).contains("valid=true", "failure=null", "detectedType=null");
        assertThat(FileValidationResult.invalid(FileValidationFailure.EMPTY, null).toString())
                .contains("valid=false", "failure=EMPTY", "detectedType=null");
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>validate()</code>\u3001<code>ooxml()</code>\u3001<code>ole()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u9a8c\u8bc1<code>renamed.getFailure()</code> \u7b49\u4e8e <code>FileValidationFailure.SIGNATURE_MISMATCH</code>\uff0c<code>executable.getFailure()</code> \u7b49\u4e8e <code>FileValidationFailure.SIGNATURE_MISMATCH</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void shouldRejectRenamedAndUnsupportedFiles() throws IOException {
        FileValidationResult renamed = validate("report.pdf", "application/pdf",
                ooxml("[Content_Types].xml", "word/document.xml"));
        FileValidationResult executable = validate("report.pdf", "application/pdf",
                new byte[]{0x4D, 0x5A, 0x00, 0x00});

        assertThat(renamed.getFailure()).isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
        assertThat(executable.getFailure()).isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
    }

    /** \u9a8c\u8bc1<code>service.validate(null, optional).isValid()</code>\u4e3a\u771f\uff0c<code>service.validate(file("report.pdf", "application/pdf", new byte[0]), policy()).getFailure()</code> \u7b49\u4e8e <code>FileValidationFailure.EMPTY</code>\uff0c<code>service.validate(file("report.pdf", "application/pdf", new byte[]{1, 2}), oneByte).getFailure()</code> \u7b49\u4e8e <code>FileValidationFailure.SIZE_EXCEEDED</code>\u3002 */
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

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>validate()</code>\u3001<code>malformedOoxml()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void shouldRejectMalformedOfficeContainer() throws IOException {
        assertThat(validate("report.docx", "application/octet-stream",
                malformedOoxml("[Content_Types].xml", "word/document.xml")).getFailure())
                .isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
    }

    /**
     * \u901a\u8fc7 <code>service.validate()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param fileName \u4f20\u7ed9 <code>service.validate()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param contentType \u4f20\u7ed9 <code>service.validate()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param content \u4f20\u7ed9 <code>service.validate()</code> \u7684 <code>byte[]</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>service.validate(file(fileName, contentType, content), policy())</code>
     */
    private FileValidationResult validate(String fileName, String contentType, byte[] content) {
        return service.validate(file(fileName, contentType, content), policy());
    }

    /**
     * \u901a\u8fc7 <code>policyBuilder()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>policyBuilder().build()</code>
     */
    private FileValidationPolicy policy() {
        return policyBuilder().build();
    }

    /**
     * \u8c03\u7528 <code>FileValidationPolicy.builder()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>FileValidationPolicy.Builder</code> \u7ed3\u679c
     */
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

    /**
     * \u521b\u5efa <code>new DefaultUploadFile</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param fileName \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @param contentType \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
     * @param content \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>byte[]</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new DefaultUploadFile("file", fileName, contentType, content.length, ()-&gt;new ByteArrayInputStream(content))</code>
     */
    private DefaultUploadFile file(String fileName, String contentType, byte[] content) {
        return new DefaultUploadFile("file", fileName, contentType, content.length,
                () -> new ByteArrayInputStream(content));
    }

    /**
     * \u901a\u8fc7 <code>output.toByteArray()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param entries \u4f20\u7ed9 <code>java.util.Arrays.asList()</code> \u7684 <code>String[]</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>output.toByteArray()</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u901a\u8fc7 <code>outputStream.toByteArray()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param entries \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>String[]</code> \u53c2\u6570 <code>entries</code>
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>outputStream.toByteArray()</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u901a\u8fc7 <code>outputStream.toByteArray()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param entryName \u4f20\u7ed9 <code>fileSystem.getRoot()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>outputStream.toByteArray()</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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
