package io.ddd4j.extension.monitor.channel.wecom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** \u901a\u8fc7 <code>client.sendMarkdown()</code> \u9a8c\u8bc1 <code>WeComFailureContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class WeComFailureContractTest {

    /** \u9a8c\u8bc1<code>client.sendMarkdown("alert")</code> \u6b63\u5e38\u8fd4\u56de\u3002 */
    @Test
    void invalidWebhookAddressDoesNotEscapeAlertSender() {
        WeComClient client = new WeComClient("key", "%%%invalid-uri");

        assertDoesNotThrow(() -> client.sendMarkdown("alert"));
    }
}
