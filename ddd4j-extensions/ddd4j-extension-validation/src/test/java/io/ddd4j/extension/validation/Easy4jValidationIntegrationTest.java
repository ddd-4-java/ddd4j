package io.ddd4j.extension.validation;

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

    @Test
    void easy4jConstraintAcceptsUploadAndRejectsRenamedContent() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(new Request(file("%PDF-1.7\nvalid")))).isEmpty();
            assertThat(factory.getValidator().validate(new Request(file("MZ executable")))).hasSize(1);
        }
    }

    @Test
    void defaultServiceExecutesEasy4jContentProvider() {
        FileValidationService service = new FileValidationService();
        FileValidationPolicy policy = FileValidationPolicy.builder().allowedExtensions("pdf").build();
        assertThat(service.validate(file("%PDF-1.7\nvalid"), policy).isValid()).isTrue();
        assertThat(service.validate(file("%PDF-1.7\nblocked"), policy).getFailure())
                .isEqualTo(FileValidationFailure.CONTENT_REJECTED);
    }

    private static DefaultUploadFile file(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
        return new DefaultUploadFile("file", "report.pdf", "application/pdf", bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private static final class Request {
        @FileNotEmpty(extensions = "pdf", strict = true)
        private final UploadFile file;

        private Request(UploadFile file) {
            this.file = file;
        }
    }
}
