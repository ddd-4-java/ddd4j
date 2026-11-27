package io.ddd4j.web.testkit.validation;

import io.github.easy4j.validation.constraints.AllowableValues;
import io.github.easy4j.validation.constraints.NumberValue;
import io.github.easy4j.validation.constraints.PhoneNumber;
import io.github.easy4j.validation.constraints.StringDateValue;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>Validation.byDefaultProvider()</code>、<code>factory.getValidator()</code>、<code>validator.validate()</code> 验证 <code>ValidationContract</code> 的运行行为。
 */
class ValidationContractTest {

    /**
     * 在测试框架中执行 <code>Validation.byDefaultProvider()</code>、<code>factory.getValidator()</code>、<code>validator.validate()</code>，验证该场景。
     */
    @Test
    void providerMustRunAllCustomConstraints() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<String> invalidProperties = validator.validate(new InvalidValues()).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());

            assertThat(invalidProperties).containsExactlyInAnyOrder("allowed", "number", "phone", "date");
        }
    }

    /**
     * 将 <code>allowed</code>、<code>number</code>、<code>phone</code>、<code>date</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class InvalidValues {

        /**
         * 测试中实际判断或预置的访问许可状态。
         */
        @AllowableValues(allows = "A,B")
        private final String allowed = "C";

        /**
         * 用于测试映射或处理逻辑的数值字段。
         */
        @NumberValue(message = "invalid number")
        private final String number = "abc";

        /**
         * 用于比较用户资料输入输出的联系电话。
         */
        @PhoneNumber(message = "invalid phone")
        private final String phone = "not a phone";

        /**
         * 用于比较日期读取与写入行为的测试时间值。
         */
        @StringDateValue(message = "invalid date")
        private final String date = "not a date";
    }
}
