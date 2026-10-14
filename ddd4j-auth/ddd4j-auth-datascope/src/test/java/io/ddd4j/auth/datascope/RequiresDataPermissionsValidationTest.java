package io.ddd4j.auth.datascope;

import io.ddd4j.auth.datascope.annotation.RequiresDataPermissions;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequiresDataPermissionsValidationTest {

    @Test
    void providerDiscoversDataPermissionConstraint() {
        try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().getConstraintsForClass(DataRequest.class)
                    .getConstraintsForProperty("data")).isNotNull();
        }
    }

    private static final class DataRequest {
        @RequiresDataPermissions(dataType = "order")
        private String data;
    }
}
