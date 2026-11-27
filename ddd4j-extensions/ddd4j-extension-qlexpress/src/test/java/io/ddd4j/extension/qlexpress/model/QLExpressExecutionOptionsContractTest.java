package io.ddd4j.extension.qlexpress.model;

import com.alibaba.qlexpress4.QLOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u901a\u8fc7 <code>attachments.put()</code>\u3001<code>QLExpressExecutionOptions.builder()</code>\u3001<code>options.toNativeOptions()</code> \u9a8c\u8bc1 <code>QLExpressExecutionOptionsContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class QLExpressExecutionOptionsContractTest {

    /** \u9a8c\u8bc1<code>nativeOptions.getAttachments().get("nullable")</code>\u4e3a\u7a7a\uff0c<code>nativeOptions.getAttachments().put("new", "value")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void nativeAttachmentsPreserveNullValuesInAnImmutableSnapshot() {
        Map<String, Object> attachments = new HashMap<>();
        attachments.put("nullable", null);
        QLExpressExecutionOptions options = QLExpressExecutionOptions.builder()
                .attachments(attachments)
                .build();

        QLOptions nativeOptions = options.toNativeOptions();
        attachments.put("later", "changed");

        assertThat(nativeOptions.getAttachments()).containsKey("nullable");
        assertThat(nativeOptions.getAttachments().get("nullable")).isNull();
        assertThat(nativeOptions.getAttachments()).doesNotContainKey("later");
        assertThatThrownBy(() -> nativeOptions.getAttachments().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
