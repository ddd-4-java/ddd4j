package io.ddd4j.extension.qlexpress.model;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>QLExpressExecutionResult.success()</code>、<code>QLExpressExecutionResult.failure()</code>、<code>JsonKit.toObject()</code> 验证 <code>QLExpressExecutionResultJsonContract</code> 的运行行为。
 */
class QLExpressExecutionResultJsonContractTest {

    /**
     * 验证<code>JsonKit.toObject(JsonKit.toJson(success), QLExpressExecutionResult.class)</code> 等于 <code>success</code>，<code>JsonKit.toObject(JsonKit.toJson(failure), QLExpressExecutionResult.class)</code> 等于 <code>failure</code>。
     */
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
