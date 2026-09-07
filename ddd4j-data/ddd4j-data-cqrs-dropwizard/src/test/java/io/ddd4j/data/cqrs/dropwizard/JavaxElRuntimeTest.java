package io.ddd4j.data.cqrs.dropwizard;

import javax.el.ExpressionFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaxElRuntimeTest {

    @Test
    void expressionFactoryShouldLoadOnJdk8() {
        assertThat(ExpressionFactory.newInstance()).isNotNull();
    }
}
