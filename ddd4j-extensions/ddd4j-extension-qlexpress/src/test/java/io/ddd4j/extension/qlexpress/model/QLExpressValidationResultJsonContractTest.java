package io.ddd4j.extension.qlexpress.model;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>QLExpressValidationResult.invalid()</code>、<code>JsonKit.toJson()</code>、<code>JsonKit.toObject()</code> 验证 <code>QLExpressValidationResultJsonContract</code> 的运行行为。
 */
class QLExpressValidationResultJsonContractTest {

    /**
     * 验证<code>decoded</code> 等于 <code>result</code>，<code>decoded.valid()</code>为假，<code>decoded.message()</code> 等于 <code>"语法错误"</code>。
     */
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
