package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.file.DefaultUploadFile;
import io.github.easy4j.validation.file.UploadFile;
import io.github.easy4j.validation.file.FileValidationService;
import io.github.easy4j.validation.file.FileValidationPolicy;
import io.github.easy4j.validation.file.FileValidationFailure;

import io.github.easy4j.validation.constraints.FileNotEmpty;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 直接使用 Easy4j 上传对象、注解和内容 SPI。 */
class Easy4jValidationIntegrationTest {

    /** \u9a8c\u8bc1<code>factory.getValidator().validate(new Request(file("%PDF-1.7\nvalid")))</code>\u4e3a\u7a7a\uff0c<code>factory.getValidator().validate(new Request(file("MZ executable")))</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void easy4jConstraintAcceptsUploadAndRejectsRenamedContent() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(new Request(file("%PDF-1.7\nvalid")))).isEmpty();
            assertThat(factory.getValidator().validate(new Request(file("MZ executable")))).hasSize(1);
        }
    }

    /** \u9a8c\u8bc1<code>service.validate(file("%PDF-1.7\nvalid"), policy).isValid()</code>\u4e3a\u771f\uff0c<code>service.validate(file("%PDF-1.7\nblocked"), policy).getFailure()</code> \u7b49\u4e8e <code>FileValidationFailure.CONTENT_REJECTED</code>\u3002 */
    @Test
    void defaultServiceExecutesEasy4jContentProvider() {
        FileValidationService service = new FileValidationService();
        FileValidationPolicy policy = FileValidationPolicy.builder().allowedExtensions("pdf").build();
        assertThat(service.validate(file("%PDF-1.7\nvalid"), policy).isValid()).isTrue();
        assertThat(service.validate(file("%PDF-1.7\nblocked"), policy).getFailure())
                .isEqualTo(FileValidationFailure.CONTENT_REJECTED);
    }

    /**
     * \u521b\u5efa <code>new DefaultUploadFile</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param content \u4f20\u7ed9 <code>content.getBytes()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new DefaultUploadFile("file", "report.pdf", "application/pdf", bytes.length, ()-&gt;new ByteArrayInputStream(bytes))</code>
     */
    private static DefaultUploadFile file(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
        return new DefaultUploadFile("file", "report.pdf", "application/pdf", bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    /** \u5c06 <code>file</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class Request {
        /** \u7528\u4f8b\u8bfb\u53d6\u6216\u5199\u5165\u7684\u4e34\u65f6\u6587\u4ef6\u3002 */
        @FileNotEmpty(extensions = "pdf", strict = true)
        private final UploadFile file;

        /**
         * \u8bbe\u7f6e <code>file</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>Request</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param file \u7528\u4e8e\u521d\u59cb\u5316 <code>Request</code> \u7684 <code>UploadFile</code> \u503c
         */
        private Request(UploadFile file) {
            this.file = file;
        }
    }
}
