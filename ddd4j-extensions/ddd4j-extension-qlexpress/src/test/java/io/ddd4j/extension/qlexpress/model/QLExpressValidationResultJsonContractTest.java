package io.ddd4j.extension.qlexpress.model;

import io.ddd4j.kit.lang.JsonKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>QLExpressValidationResult.invalid()</code>\u3001<code>JsonKit.toJson()</code>\u3001<code>JsonKit.toObject()</code> \u9a8c\u8bc1 <code>QLExpressValidationResultJsonContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class QLExpressValidationResultJsonContractTest {

    /** \u9a8c\u8bc1<code>decoded</code> \u7b49\u4e8e <code>result</code>\uff0c<code>decoded.valid()</code>\u4e3a\u5047\uff0c<code>decoded.message()</code> \u7b49\u4e8e <code>"\u8bed\u6cd5\u9519\u8bef"</code>\u3002 */
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
