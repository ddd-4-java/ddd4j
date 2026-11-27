package io.ddd4j.extension.qlexpress.model;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QLExpressExecutionResultJsonContractTest {

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
