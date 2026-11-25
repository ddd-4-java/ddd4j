package io.ddd4j.extension.validation;

import io.github.easy4j.validation.constraints.RequiresDataPermissions;
import io.github.easy4j.validation.constraintvalidators.RequiresDataPermissionsValidator;
import io.github.easy4j.validation.file.DefaultUploadFile;
import io.github.easy4j.validation.constraints.FileNotEmpty;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 单个 validation 模块中执行数据权限与 Easy4j 文件约束。 */
class DataPermissionValidationIntegrationTest {
    @Test
    void missingProviderMustNotAuthorizeNonNullData() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThatThrownBy(() -> factory.getValidator().validate(new Request("denied", "%PDF-1.7")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void missingProviderIsConfigurationErrorEvenForNullData() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThatThrownBy(() -> factory.getValidator().validate(new Request(null, "%PDF-1.7")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void realValidationUsesCustomProviderAlongsideEasy4jFileConstraint() {
        javax.validation.Configuration<?> configuration = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator());
        ConstraintValidatorFactory defaults = configuration.getDefaultConstraintValidatorFactory();
        ConstraintValidatorFactory permissions = new ConstraintValidatorFactory() {
            @Override
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> type) {
                if (type.equals(RequiresDataPermissionsValidator.class)) {
                    return type.cast(new RequiresDataPermissionsValidator(
                            (domain, value) -> "order".equals(domain) && "allowed".equals(value)));
                }
                return defaults.getInstance(type);
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instance) {
                if (!(instance instanceof RequiresDataPermissionsValidator)) {
                    defaults.releaseInstance(instance);
                }
            }
        };
        try (ValidatorFactory factory = configuration.constraintValidatorFactory(permissions).buildValidatorFactory()) {
            javax.validation.Validator validator = factory.getValidator();
            assertThat(validator.validate(new Request("allowed", "%PDF-1.7"))).isEmpty();
            assertThat(validator.validate(new Request("denied", "%PDF-1.7")))
                    .singleElement().satisfies(v -> {
                        assertThat(v.getPropertyPath().toString()).isEqualTo("orderId");
                        assertThat(v.getMessage()).isEqualTo("订单不可见");
                    });
            assertThat(validator.validate(new Request(null, "%PDF-1.7"))).hasSize(1);
            assertThat(validator.validate(new Request("allowed", "MZ executable")))
                    .singleElement().satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("file"));
        }
    }

    private static final class Request {
        @RequiresDataPermissions(dataType = "order", message = "订单不可见")
        private final String orderId;
        @FileNotEmpty(extensions = "pdf", strict = true)
        private final DefaultUploadFile file;

        private Request(String orderId, String content) {
            this.orderId = orderId;
            byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
            this.file = new DefaultUploadFile("file", "file.pdf", "application/pdf", bytes.length,
                    () -> new ByteArrayInputStream(bytes));
        }
    }
}
