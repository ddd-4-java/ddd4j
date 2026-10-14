package io.ddd4j.web.validation;

import io.ddd4j.web.validation.constraints.AllowableValues;
import io.ddd4j.web.validation.constraints.NumberValue;
import io.ddd4j.web.validation.constraints.PhoneNumber;
import io.ddd4j.web.validation.constraints.StringDateValue;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationContractTest {

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

    private static final class InvalidValues {

        @AllowableValues(allows = "A,B")
        private final String allowed = "C";

        @NumberValue(message = "invalid number")
        private final String number = "abc";

        @PhoneNumber(message = "invalid phone")
        private final String phone = "not a phone";

        @StringDateValue(message = "invalid date")
        private final String date = "not a date";
    }
}
