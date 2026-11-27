package io.ddd4j.extension.validation;

import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 识别 PDF、Microsoft Office OLE2 与 OOXML 文件的内容检测器。
 */
public final class OfficeFileTypeDetector implements FileTypeDetector {

    /** 保存 <code>{'%', 'P', 'D', 'F', '-'}</code>，供 <code>OfficeFileTypeDetector</code> 的处理流程复用。 */
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    /** 保存 <code>{ (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 }</code>，供 <code>OfficeFileTypeDetector</code> 的处理流程复用。 */
    private static final byte[] OLE_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    /** 保存 <code>512</code>，供 <code>OfficeFileTypeDetector</code> 的处理流程复用。 */
    private static final int MAX_ZIP_ENTRIES_TO_INSPECT = 512;

    /**
     * 执行 <code>detect</code>，调用 <code>Optional.of</code>、<code>Optional.empty</code>。
     *
     * @param file 传入此操作的 <code>ValidatableFile</code> 类型 <code>file</code>
     * @return 按条件返回 <code>Optional.of(new DetectedFileType("pdf", "application/pdf"))</code>、<code>detectOoxml(file)</code>、<code>detectOle(file)</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    @Override
    public Optional<DetectedFileType> detect(ValidatableFile file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        byte[] signature = readSignature(file, OLE_SIGNATURE.length);
        if (startsWith(signature, PDF_SIGNATURE)) {
            return Optional.of(new DetectedFileType("pdf", "application/pdf"));
        }
        if (isZip(signature)) {
            return detectOoxml(file);
        }
        if (startsWith(signature, OLE_SIGNATURE)) {
            return detectOle(file);
        }
        return Optional.empty();
    }

    /**
     * 执行 <code>read Signature</code>，调用 <code>file.openStream</code>、<code>inputStream.readNBytes</code>。
     *
     * @param file 传入此操作的 <code>ValidatableFile</code> 类型 <code>file</code>
     * @param size 传入此操作的 <code>int</code> 类型 <code>size</code>
     * @return 返回表达式 <code>inputStream.readNBytes(size)</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private byte[] readSignature(ValidatableFile file, int size) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(file.openStream())) {
            return inputStream.readNBytes(size);
        }
    }

    /**
     * 执行 <code>detect Ooxml</code>，调用 <code>file.openStream</code>、<code>zipInputStream.getNextEntry</code>、<code>entry.getName</code>。
     *
     * @param file 传入此操作的 <code>ValidatableFile</code> 类型 <code>file</code>
     * @return 返回表达式 <code>Optional.empty()</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private Optional<DetectedFileType> detectOoxml(ValidatableFile file) throws IOException {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        boolean hasExcelWorkbook = false;
        int inspectedEntries = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(file.openStream()))) {
            ZipEntry entry;
            while (Objects.nonNull(entry = zipInputStream.getNextEntry())
                    && inspectedEntries++ < MAX_ZIP_ENTRIES_TO_INSPECT) {
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                hasContentTypes |= "[content_types].xml".equals(name);
                hasWordDocument |= "word/document.xml".equals(name);
                hasExcelWorkbook |= "xl/workbook.xml".equals(name);
            }
        }

        if (hasContentTypes && hasWordDocument) {
            return Optional.of(new DetectedFileType("docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        }
        if (hasContentTypes && hasExcelWorkbook) {
            return Optional.of(new DetectedFileType("xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        }
        return Optional.empty();
    }

    /**
     * 执行 <code>detect Ole</code>，调用 <code>file.openStream</code>、<code>fileSystem.getRoot</code>、<code>root.hasEntry</code>。
     *
     * @param file 传入此操作的 <code>ValidatableFile</code> 类型 <code>file</code>
     * @return 按条件返回 <code>Optional.of(new DetectedFileType("doc", "application/msword"))</code>、<code>Optional.of(new DetectedFileType("xls", "application/vnd.ms-excel"))</code>、<code>Optional.empty()</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private Optional<DetectedFileType> detectOle(ValidatableFile file) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(file.openStream());
             POIFSFileSystem fileSystem = new POIFSFileSystem(inputStream)) {
            DirectoryNode root = fileSystem.getRoot();
            if (root.hasEntry("WordDocument")) {
                return Optional.of(new DetectedFileType("doc", "application/msword"));
            }
            if (root.hasEntry("Workbook") || root.hasEntry("Book")) {
                return Optional.of(new DetectedFileType("xls", "application/vnd.ms-excel"));
            }
            return Optional.empty();
        }
    }

    /**
     * 按 <code>starts With</code> 的契约计算并返回结果。
     *
     * @param source 传入此操作的 <code>byte[]</code> 类型 <code>source</code>
     * @param prefix 传入此操作的 <code>byte[]</code> 类型 <code>prefix</code>
     * @return 按条件返回 <code>false</code>、<code>false</code>、<code>true</code>
     */
    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (source[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行 <code>is Zip</code> 对应的处理。
     *
     * @param signature 传入此操作的 <code>byte[]</code> 类型 <code>signature</code>
     * @return 该判断是否成立
     */
    private boolean isZip(byte[] signature) {
        return signature.length >= 4
                && signature[0] == 'P'
                && signature[1] == 'K'
                && ((signature[2] == 3 && signature[3] == 4)
                || (signature[2] == 5 && signature[3] == 6)
                || (signature[2] == 7 && signature[3] == 8));
    }
}
