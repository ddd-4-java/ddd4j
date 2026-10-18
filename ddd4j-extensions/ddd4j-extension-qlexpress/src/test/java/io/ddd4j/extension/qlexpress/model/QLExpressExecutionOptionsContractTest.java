package io.ddd4j.extension.qlexpress.model;

import com.alibaba.qlexpress4.QLOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QLExpressExecutionOptionsContractTest {

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
