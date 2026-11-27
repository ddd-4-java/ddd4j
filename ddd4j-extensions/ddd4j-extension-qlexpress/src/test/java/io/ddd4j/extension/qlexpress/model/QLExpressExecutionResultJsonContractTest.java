package io.ddd4j.extension.qlexpress.model;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>QLExpressExecutionResult.success()</code>\u3001<code>QLExpressExecutionResult.failure()</code>\u3001<code>JsonKit.toObject()</code> \u9a8c\u8bc1 <code>QLExpressExecutionResultJsonContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class QLExpressExecutionResultJsonContractTest {

    /** \u9a8c\u8bc1<code>JsonKit.toObject(JsonKit.toJson(success), QLExpressExecutionResult.class)</code> \u7b49\u4e8e <code>success</code>\uff0c<code>JsonKit.toObject(JsonKit.toJson(failure), QLExpressExecutionResult.class)</code> \u7b49\u4e8e <code>failure</code>\u3002 */
    @Test
    void successAndFailureRoundTripThroughJson() {
        QLExpressExecutionResult<String> success = QLExpressExecutionResult.success("accepted", 7L);
        QLExpressExecutionResult<String> failure = QLExpressExecutionResult.failure("SyntaxError", "invalid", 11L);

        assertThat(JsonKit.toObject(JsonKit.toJson(success), QLExpressExecutionResult.class))
                .isEqualTo(success);
        assertThat(JsonKit.toObject(JsonKit.toJson(failure), QLExpressExecutionResult.class))
                .isEqualTo(failure);
    }
}
