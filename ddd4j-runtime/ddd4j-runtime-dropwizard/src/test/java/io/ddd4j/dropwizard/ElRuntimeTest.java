package io.ddd4j.dropwizard;

import jakarta.el.ExpressionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 <code>ElRuntime</code> 在 <code>expressionFactory</code> 等场景中的行为。
 */
class ElRuntimeTest {

    /**
     * 验证<code>ExpressionFactory.newInstance()</code>非空。
     */
    @Test
    void expressionFactoryShouldLoadFromCurrentPlatform() {
        assertThat(ExpressionFactory.newInstance()).isNotNull();
    }
}
