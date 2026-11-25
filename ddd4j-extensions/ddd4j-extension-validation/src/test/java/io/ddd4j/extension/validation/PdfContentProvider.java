package io.ddd4j.extension.validation;

import io.github.easy4j.validation.file.UploadFile;
import io.github.easy4j.validation.provider.FileContentCheckProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 测试用真实 SPI 内容规则：拒绝带 blocked 标记的 PDF。 */
public final class PdfContentProvider implements FileContentCheckProvider {
    @Override
    public Boolean check(UploadFile file) {
        try {
            return !new String(file.getBytes(), StandardCharsets.US_ASCII).contains("blocked");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public String support() {
        return "pdf";
    }
}
