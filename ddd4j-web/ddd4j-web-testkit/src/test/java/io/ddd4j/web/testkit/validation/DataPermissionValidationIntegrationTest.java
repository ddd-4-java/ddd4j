package io.ddd4j.web.testkit.validation;

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
    /** \u9a8c\u8bc1<code>factory.getValidator().validate(new Request("denied", "%PDF-1.7"))</code> \u629b\u51fa <code>ValidationException.class</code>\u3002 */
    @Test
    void missingProviderMustNotAuthorizeNonNullData() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThatThrownBy(() -> factory.getValidator().validate(new Request("denied", "%PDF-1.7")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    /** \u9a8c\u8bc1<code>factory.getValidator().validate(new Request(null, "%PDF-1.7"))</code> \u629b\u51fa <code>ValidationException.class</code>\u3002 */
    @Test
    void missingProviderIsConfigurationErrorEvenForNullData() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThatThrownBy(() -> factory.getValidator().validate(new Request(null, "%PDF-1.7")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    /** \u9a8c\u8bc1<code>validator.validate(new Request("allowed", "%PDF-1.7"))</code>\u4e3a\u7a7a\uff0c<code>v.getPropertyPath().toString()</code> \u7b49\u4e8e <code>"orderId"</code>\uff0c<code>v.getPropertyPath().toString()</code> \u7b49\u4e8e <code>"file"</code>\u3002 */
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

    /** \u5c06 <code>orderId</code>\u3001<code>file</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class Request {
        /** \u8ba2\u5355\u6807\u8bc6\u3002 */
        @RequiresDataPermissions(dataType = "order", message = "订单不可见")
        private final String orderId;
        /** \u7528\u4f8b\u8bfb\u53d6\u6216\u5199\u5165\u7684\u4e34\u65f6\u6587\u4ef6\u3002 */
        @FileNotEmpty(extensions = "pdf", strict = true)
        private final DefaultUploadFile file;

        /**
         * \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>Request</code> \u5b9e\u4f8b\u3002
         * @param orderId \u8ba2\u5355\u6807\u8bc6
         * @param content \u7528\u4e8e\u521d\u59cb\u5316 <code>Request</code> \u7684 <code>String</code> \u503c
         */
        private Request(String orderId, String content) {
            this.orderId = orderId;
            byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
            this.file = new DefaultUploadFile("file", "file.pdf", "application/pdf", bytes.length,
                    () -> new ByteArrayInputStream(bytes));
        }
    }
}
