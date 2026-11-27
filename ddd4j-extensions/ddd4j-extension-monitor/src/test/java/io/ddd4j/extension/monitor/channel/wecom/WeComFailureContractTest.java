package io.ddd4j.extension.monitor.channel.wecom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 通过 <code>client.sendMarkdown()</code> 验证 <code>WeComFailureContract</code> 的运行行为。
 */
class WeComFailureContractTest {

    /**
     * 验证<code>client.sendMarkdown("alert")</code> 正常返回。
     */
    @Test
    void invalidWebhookAddressDoesNotEscapeAlertSender() {
        WeComClient client = new WeComClient("key", "%%%invalid-uri");

        assertDoesNotThrow(() -> client.sendMarkdown("alert"));
    }
}
