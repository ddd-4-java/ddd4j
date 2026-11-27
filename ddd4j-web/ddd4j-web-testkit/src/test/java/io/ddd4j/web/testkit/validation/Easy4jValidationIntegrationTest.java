package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.constraints.FileNotEmpty;
import io.github.easy4j.validation.file.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 直接使用 Easy4j 上传对象、注解和内容 SPI。
 */
class Easy4jValidationIntegrationTest {

    /**
     * 创建 <code>new DefaultUploadFile</code> 供当前测试路径调用。
     *
     * @param content 传给 <code>content.getBytes()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>new DefaultUploadFile("file", "report.pdf", "application/pdf", bytes.length, ()-&gt;new ByteArrayInputStream(bytes))</code>
     */
    private static DefaultUploadFile file(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
        return new DefaultUploadFile("file", "report.pdf", "application/pdf", bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    /**
     * 验证<code>factory.getValidator().validate(new Request(file("%PDF-1.7\nvalid")))</code>为空，<code>factory.getValidator().validate(new Request(file("MZ executable")))</code> 的大小为 <code>1</code>。
     */
    @Test
    void easy4jConstraintAcceptsUploadAndRejectsRenamedContent() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(new Request(file("%PDF-1.7\nvalid")))).isEmpty();
            assertThat(factory.getValidator().validate(new Request(file("MZ executable")))).hasSize(1);
        }
    }

    /**
     * 验证<code>service.validate(file("%PDF-1.7\nvalid"), policy).isValid()</code>为真，<code>service.validate(file("%PDF-1.7\nblocked"), policy).getFailure()</code> 等于 <code>FileValidationFailure.CONTENT_REJECTED</code>。
     */
    @Test
    void defaultServiceExecutesEasy4jContentProvider() {
        FileValidationService service = new FileValidationService();
        FileValidationPolicy policy = FileValidationPolicy.builder().allowedExtensions("pdf").build();
        assertThat(service.validate(file("%PDF-1.7\nvalid"), policy).isValid()).isTrue();
        assertThat(service.validate(file("%PDF-1.7\nblocked"), policy).getFailure())
                .isEqualTo(FileValidationFailure.CONTENT_REJECTED);
    }

    /**
     * 将 <code>file</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class Request {
        /**
         * 用例读取或写入的临时文件。
         */
        @FileNotEmpty(extensions = "pdf", strict = true)
        private final UploadFile file;

        /**
         * 设置 <code>file</code>，用于构造 <code>Request</code> 测试实例。
         *
         * @param file 用于初始化 <code>Request</code> 的 <code>UploadFile</code> 值
         */
        private Request(UploadFile file) {
            this.file = file;
        }
    }
}
