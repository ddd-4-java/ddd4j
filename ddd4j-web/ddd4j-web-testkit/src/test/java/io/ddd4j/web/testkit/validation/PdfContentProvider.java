package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.file.UploadFile;
import io.github.easy4j.validation.provider.FileContentCheckProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 测试用真实 SPI 内容规则：拒绝带 blocked 标记的 PDF。
 */
public final class PdfContentProvider implements FileContentCheckProvider {
    /**
     * 调用 <code>file.getBytes()</code> 执行当前夹具操作。
     *
     * @param file 传给 <code>new String</code> 的 <code>UploadFile</code> 输入
     * @return 当前测试使用的结果，取自 <code>!new String(file.getBytes(), StandardCharsets.US_ASCII).contains("blocked")</code>
     */
    @Override
    public Boolean check(UploadFile file) {
        try {
            return !new String(file.getBytes(), StandardCharsets.US_ASCII).contains("blocked");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 为当前用例提供固定的 <code>support</code> 返回值。
     *
     * @return <code>"pdf"</code>
     */
    @Override
    public String support() {
        return "pdf";
    }
}
