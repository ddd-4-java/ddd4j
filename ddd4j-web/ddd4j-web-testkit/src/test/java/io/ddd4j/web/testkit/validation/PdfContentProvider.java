package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.file.UploadFile;
import io.github.easy4j.validation.provider.FileContentCheckProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 测试用真实 SPI 内容规则：拒绝带 blocked 标记的 PDF。 */
public final class PdfContentProvider implements FileContentCheckProvider {
    /**
     * \u8c03\u7528 <code>file.getBytes()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param file \u4f20\u7ed9 <code>new String</code> \u7684 <code>UploadFile</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>!new String(file.getBytes(), StandardCharsets.US_ASCII).contains("blocked")</code>
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
     * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>support</code> \u8fd4\u56de\u503c\u3002
     * @return <code>"pdf"</code>
     */
    @Override
    public String support() {
        return "pdf";
    }
}
