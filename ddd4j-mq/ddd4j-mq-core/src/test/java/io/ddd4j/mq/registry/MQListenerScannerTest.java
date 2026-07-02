package io.ddd4j.mq.registry;

import io.ddd4j.core.contract.annotation.MQEventListener;
import io.ddd4j.mq.ack.AckDisposition;
import io.ddd4j.mq.consume.MQConsumerContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link MQListenerScanner} 单元测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class MQListenerScannerTest {

    @Test
    void scanShouldDiscoverAnnotatedMethods() throws Exception {
        MQListenerDefinitionRegistry registry = new MQListenerDefinitionRegistry();

        // 手动注册监听器定义（模拟 BeanPostProcessor 行为）
        DemoListener listener = new DemoListener();
        Method method = DemoListener.class.getMethod("onDemo", MQConsumerContext.class, DemoEvent.class);
        MQEventListener annotation = method.getAnnotation(MQEventListener.class);

        registry.register(MQListenerDefinition.builder()
                .bean(listener)
                .beanName("demoListener")
                .method(method)
                .topic(annotation.topic())
                .tags(annotation.tags())
                .group(annotation.group())
                .namespace(annotation.namespace())
                .build());

        MQListenerScanner scanner = new MQListenerScanner(registry);
        List<MQListenerDefinition> definitions = scanner.scan();

        assertFalse(definitions.isEmpty());
        assertEquals(1, definitions.size());
        MQListenerDefinition definition = definitions.get(0);
        assertEquals("demoTopic", definition.getTopic());
        assertEquals("demoGroup", definition.getGroup());
        assertEquals("test-ns", definition.getNamespace());
        assertEquals("onDemo", definition.getMethod().getName());
    }

    static class DemoListener {

        @MQEventListener(topic = "demoTopic", tags = "create", group = "demoGroup", namespace = "test-ns")
        public AckDisposition onDemo(MQConsumerContext ctx, DemoEvent event) {
            return AckDisposition.ACK;
        }
    }

    static class DemoEvent {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
