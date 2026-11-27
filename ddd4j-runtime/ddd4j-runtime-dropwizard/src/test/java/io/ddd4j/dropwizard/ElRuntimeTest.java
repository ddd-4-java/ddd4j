package io.ddd4j.dropwizard;

import org.junit.jupiter.api.Test;

import javax.el.ExpressionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** \u9a8c\u8bc1 <code>ElRuntime</code> \u5728 <code>expressionFactory</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class ElRuntimeTest {

    /** \u9a8c\u8bc1<code>ExpressionFactory.newInstance()</code>\u975e\u7a7a\u3002 */
    @Test
    void expressionFactoryShouldLoadFromCurrentPlatform() {
        assertThat(ExpressionFactory.newInstance()).isNotNull();
    }
}
