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

/** \u901a\u8fc7 <code>Validation.byDefaultProvider()</code>\u3001<code>factory.getValidator()</code>\u3001<code>validator.validate()</code> \u9a8c\u8bc1 <code>ValidationContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class ValidationContractTest {

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>Validation.byDefaultProvider()</code>\u3001<code>factory.getValidator()</code>\u3001<code>validator.validate()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u5c06 <code>allowed</code>\u3001<code>number</code>\u3001<code>phone</code>\u3001<code>date</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class InvalidValues {

        /** \u6d4b\u8bd5\u4e2d\u5b9e\u9645\u5224\u65ad\u6216\u9884\u7f6e\u7684\u8bbf\u95ee\u8bb8\u53ef\u72b6\u6001\u3002 */
        @AllowableValues(allows = "A,B")
        private final String allowed = "C";

        /** \u7528\u4e8e\u6d4b\u8bd5\u6620\u5c04\u6216\u5904\u7406\u903b\u8f91\u7684\u6570\u503c\u5b57\u6bb5\u3002 */
        @NumberValue(message = "invalid number")
        private final String number = "abc";

        /** \u7528\u4e8e\u6bd4\u8f83\u7528\u6237\u8d44\u6599\u8f93\u5165\u8f93\u51fa\u7684\u8054\u7cfb\u7535\u8bdd\u3002 */
        @PhoneNumber(message = "invalid phone")
        private final String phone = "not a phone";

        /** \u7528\u4e8e\u6bd4\u8f83\u65e5\u671f\u8bfb\u53d6\u4e0e\u5199\u5165\u884c\u4e3a\u7684\u6d4b\u8bd5\u65f6\u95f4\u503c\u3002 */
        @StringDateValue(message = "invalid date")
        private final String date = "not a date";
    }
}
