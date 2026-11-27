package io.ddd4j.extension.monitor.channel.wecom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WeComFailureContractTest {

    @Test
    void invalidWebhookAddressDoesNotEscapeAlertSender() {
        WeComClient client = new WeComClient("key", "%%%invalid-uri");

        assertDoesNotThrow(() -> client.sendMarkdown("alert"));
    }
}
