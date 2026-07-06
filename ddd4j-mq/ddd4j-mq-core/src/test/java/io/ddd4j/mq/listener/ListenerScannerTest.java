package io.ddd4j.mq.listener;

import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.consume.AckType;
import io.ddd4j.mq.consume.ConsumerContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link ListenerScanner} 单元测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ListenerScannerTest {

    @Test
    void scanShouldDiscoverAnnotatedMethods() throws Exception {
        ListenerDefinitionRegistry registry = new ListenerDefinitionRegistry();

        // 手动注册监听器定义（模拟 BeanPostProcessor 行为）
        DemoListener listener = new DemoListener();
        Method method = DemoListener.class.getMethod("onDemo", ConsumerContext.class, DemoEvent.class);
        MQEventListener annotation = method.getAnnotation(MQEventListener.class);

        registry.register(ListenerDefinition.builder()
                .bean(listener)
                .beanName("demoListener")
                .method(method)
                .topic(annotation.topic())
                .tags(annotation.tags())
                .group(annotation.group())
                .namespace(annotation.namespace())
                .build());

        ListenerScanner scanner = new ListenerScanner(registry);
        List<ListenerDefinition> definitions = scanner.scan();

        assertFalse(definitions.isEmpty());
        assertEquals(1, definitions.size());
        ListenerDefinition definition = definitions.get(0);
        assertEquals("demoTopic", definition.getTopic());
        assertEquals("demoGroup", definition.getGroup());
        assertEquals("test-ns", definition.getNamespace());
        assertEquals("onDemo", definition.getMethod().getName());
    }

    static class DemoListener {

        @MQEventListener(topic = "demoTopic", tags = "create", group = "demoGroup", namespace = "test-ns")
        public AckType onDemo(ConsumerContext ctx, DemoEvent event) {
            return AckType.ACK;
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
