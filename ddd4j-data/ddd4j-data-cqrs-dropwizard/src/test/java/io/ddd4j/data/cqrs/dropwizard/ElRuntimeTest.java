package io.ddd4j.data.cqrs.dropwizard;

import org.junit.jupiter.api.Test;

import jakarta.el.ExpressionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ElRuntimeTest {

    @Test
    void expressionFactoryShouldLoadFromCurrentPlatform() {
        assertThat(ExpressionFactory.newInstance()).isNotNull();
    }
}
