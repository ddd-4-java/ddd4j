package io.ddd4j.extension.qlexpress.model;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QLExpressValidationResultJsonContractTest {

    @Test
    void validationResultRoundTripsThroughJson() {
        QLExpressValidationResult result = QLExpressValidationResult.invalid("语法错误");

        String json = JsonKit.toJson(result);
        QLExpressValidationResult decoded = JsonKit.toObject(json, QLExpressValidationResult.class);

        assertThat(decoded).isEqualTo(result);
        assertThat(decoded.valid()).isFalse();
        assertThat(decoded.message()).isEqualTo("语法错误");
    }
}
