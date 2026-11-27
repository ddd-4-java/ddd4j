package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.constraints.FileNotEmpty;
import io.github.easy4j.validation.constraints.RequiresDataPermissions;
import io.github.easy4j.validation.constraintvalidators.RequiresDataPermissionsValidator;
import io.github.easy4j.validation.file.DefaultUploadFile;
import jakarta.validation.*;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单个 validation 模块中执行数据权限与 Easy4j 文件约束。
 */
class DataPermissionValidationIntegrationTest {
    /**
     * 验证<code>factory.getValidator().validate(new Request("denied", "%PDF-1.7"))</code> 抛出 <code>ValidationException.class</code>。
     */
    @Test
    void missingProviderMustNotAuthorizeNonNullData() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThatThrownBy(() -> factory.getValidator().validate(new Request("denied", "%PDF-1.7")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    /**
     * 验证<code>factory.getValidator().validate(new Request(null, "%PDF-1.7"))</code> 抛出 <code>ValidationException.class</code>。
     */
    @Test
    void missingProviderIsConfigurationErrorEvenForNullData() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThatThrownBy(() -> factory.getValidator().validate(new Request(null, "%PDF-1.7")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    /**
     * 验证<code>validator.validate(new Request("allowed", "%PDF-1.7"))</code>为空，<code>v.getPropertyPath().toString()</code> 等于 <code>"orderId"</code>，<code>v.getPropertyPath().toString()</code> 等于 <code>"file"</code>。
     */
    @Test
    void realValidationUsesCustomProviderAlongsideEasy4jFileConstraint() {
        var configuration = Validation.byDefaultProvider().configure()
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
            var validator = factory.getValidator();
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

    /**
     * 将 <code>orderId</code>、<code>file</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class Request {
        /**
         * 订单标识。
         */
        @RequiresDataPermissions(dataType = "order", message = "订单不可见")
        private final String orderId;
        /**
         * 用例读取或写入的临时文件。
         */
        @FileNotEmpty(extensions = "pdf", strict = true)
        private final DefaultUploadFile file;

        /**
         * 构造供当前测试使用的 <code>Request</code> 实例。
         *
         * @param orderId 订单标识
         * @param content 用于初始化 <code>Request</code> 的 <code>String</code> 值
         */
        private Request(String orderId, String content) {
            this.orderId = orderId;
            byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
            this.file = new DefaultUploadFile("file", "file.pdf", "application/pdf", bytes.length,
                    () -> new ByteArrayInputStream(bytes));
        }
    }
}
