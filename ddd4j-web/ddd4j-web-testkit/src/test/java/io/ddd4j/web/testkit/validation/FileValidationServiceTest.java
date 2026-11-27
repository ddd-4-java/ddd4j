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

import io.github.easy4j.validation.file.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>FileValidationResult.valid()</code>、<code>FileValidationResult.invalid()</code>、<code>validate()</code> 验证 <code>FileValidationService</code> 的运行行为。
 */
class FileValidationServiceTest {

    /**
     * 当前测试调用或验证生命周期的应用服务。
     */
    private final FileValidationService service = new FileValidationService();

    /**
     * 验证<code>FileValidationResult.valid(null).toString()</code> 包含 <code>"valid=true"</code>、<code>"failure=null"</code>、<code>"detectedType=null"</code>，<code>FileValidationResult.invalid(FileValidationFailure.EMPTY, null).toString()</code> 包含 <code>"valid=false"</code>、<code>"failure=EMPTY"</code>、<code>"detectedType=null"</code>。
     */
    @Test
    void valueStringIncludesFieldNamesForSuccessAndFailure() {
        assertThat(FileValidationResult.valid(null).toString()).contains("valid=true", "failure=null", "detectedType=null");
        assertThat(FileValidationResult.invalid(FileValidationFailure.EMPTY, null).toString())
                .contains("valid=false", "failure=EMPTY", "detectedType=null");
    }

    /**
     * 在测试框架中执行 <code>validate()</code>、<code>ooxml()</code>、<code>ole()</code>，验证该场景。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>renamed.getFailure()</code> 等于 <code>FileValidationFailure.SIGNATURE_MISMATCH</code>，<code>executable.getFailure()</code> 等于 <code>FileValidationFailure.SIGNATURE_MISMATCH</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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

    /**
     * 验证<code>service.validate(null, optional).isValid()</code>为真，<code>service.validate(file("report.pdf", "application/pdf", new byte[0]), policy()).getFailure()</code> 等于 <code>FileValidationFailure.EMPTY</code>，<code>service.validate(file("report.pdf", "application/pdf", new byte[]{1, 2}), oneByte).getFailure()</code> 等于 <code>FileValidationFailure.SIZE_EXCEEDED</code>。
     */
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
     * 在测试框架中执行 <code>validate()</code>、<code>malformedOoxml()</code>，验证该场景。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    @Test
    void shouldRejectMalformedOfficeContainer() throws IOException {
        assertThat(validate("report.docx", "application/octet-stream",
                malformedOoxml("[Content_Types].xml", "word/document.xml")).getFailure())
                .isEqualTo(FileValidationFailure.SIGNATURE_MISMATCH);
    }

    /**
     * 通过 <code>service.validate()</code> 取得当前测试需要的结果。
     *
     * @param fileName    传给 <code>service.validate()</code> 的 <code>String</code> 输入
     * @param contentType 传给 <code>service.validate()</code> 的 <code>String</code> 输入
     * @param content     传给 <code>service.validate()</code> 的 <code>byte[]</code> 输入
     * @return 当前测试使用的结果，取自 <code>service.validate(file(fileName, contentType, content), policy())</code>
     */
    private FileValidationResult validate(String fileName, String contentType, byte[] content) {
        return service.validate(file(fileName, contentType, content), policy());
    }

    /**
     * 通过 <code>policyBuilder()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>policyBuilder().build()</code>
     */
    private FileValidationPolicy policy() {
        return policyBuilder().build();
    }

    /**
     * 调用 <code>FileValidationPolicy.builder()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>FileValidationPolicy.Builder</code> 结果
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
     * 创建 <code>new DefaultUploadFile</code> 供当前测试路径调用。
     *
     * @param fileName    用于构造本次返回结果的 <code>String</code> 输入
     * @param contentType 用于构造本次返回结果的 <code>String</code> 输入
     * @param content     用于构造本次返回结果的 <code>byte[]</code> 输入
     * @return 当前测试使用的结果，取自 <code>new DefaultUploadFile("file", fileName, contentType, content.length, ()-&gt;new ByteArrayInputStream(content))</code>
     */
    private DefaultUploadFile file(String fileName, String contentType, byte[] content) {
        return new DefaultUploadFile("file", fileName, contentType, content.length,
                () -> new ByteArrayInputStream(content));
    }

    /**
     * 通过 <code>output.toByteArray()</code> 取得当前测试需要的结果。
     *
     * @param entries 传给 <code>java.util.Arrays.asList()</code> 的 <code>String[]</code> 输入
     * @return 当前测试使用的结果，取自 <code>output.toByteArray()</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 通过 <code>outputStream.toByteArray()</code> 取得当前测试需要的结果。
     *
     * @param entries 本次调用使用的 <code>String[]</code> 参数 <code>entries</code>
     * @return 当前测试使用的结果，取自 <code>outputStream.toByteArray()</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 通过 <code>outputStream.toByteArray()</code> 取得当前测试需要的结果。
     *
     * @param entryName 传给 <code>fileSystem.getRoot()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>outputStream.toByteArray()</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private byte[] ole(String entryName) throws IOException {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            fileSystem.getRoot().createDocument(entryName,
                    new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
            fileSystem.writeFilesystem(outputStream);
            return outputStream.toByteArray();
        }
    }
}
