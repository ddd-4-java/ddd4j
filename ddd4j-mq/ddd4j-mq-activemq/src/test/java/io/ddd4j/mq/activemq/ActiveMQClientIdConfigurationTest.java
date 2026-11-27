package io.ddd4j.mq.activemq;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证设置 Artemis 连接工厂客户端 ID 时只读取一次调用方配置。
 */
class ActiveMQClientIdConfigurationTest {

    /**
     * 验证<code>reads.get()</code> 等于 <code>1</code>。
     */
    @Test
    void readsClientIdPrefixOnceWhenSettingFactoryClientId() {
        AtomicInteger reads = new AtomicInteger();
        ActiveMQProperties properties = new ActiveMQProperties() {
            @Override
            public String getClientIdPrefix() {
                return reads.incrementAndGet() == 1 ? "configured-client-id" : null;
            }

            @Override
            public String toString() {
                // 日志格式化不参与连接工厂选择客户端 ID 的读取计数。
                return "ActiveMQ test properties";
            }
        };

        try (ActiveMQClient client = new ActiveMQClient(properties)) {
            assertEquals(1, reads.get(), "Read the caller-supplied ID once when setting the factory client ID");
        }
    }
}
