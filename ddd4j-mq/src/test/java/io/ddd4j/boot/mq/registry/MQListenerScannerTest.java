package io.ddd4j.boot.mq.registry;

import io.ddd4j.boot.core.contract.annotation.MQEventListener;
import io.ddd4j.boot.mq.acknowledgment.AckDisposition;
import io.ddd4j.boot.mq.config.Ddd4jMQProperties;
import io.ddd4j.boot.mq.registry.MQListenerBeanPostProcessor;
import io.ddd4j.boot.mq.registry.MQListenerDefinitionRegistry;
import io.ddd4j.boot.mq.consume.MQConsumerContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link MQListenerScanner} 单元测试。
 */
class MQListenerScannerTest {

    @Test
    void scanShouldDiscoverAnnotatedMethods() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            Ddd4jMQProperties props = context.getBean(Ddd4jMQProperties.class);
            MQListenerScanner scanner = new MQListenerScanner(context.getBean(MQListenerDefinitionRegistry.class));

            var definitions = scanner.scan();

            assertFalse(definitions.isEmpty());
            assertEquals(1, definitions.size());
            MQListenerDefinition definition = definitions.get(0);
            assertEquals("demoTopic", definition.getTopic());
            assertEquals("demoGroup", definition.getGroup());
            assertEquals("test-ns", definition.getNamespace());
            assertEquals("onDemo", definition.getMethod().getName());
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        Ddd4jMQProperties ddd4jMQProperties() {
            Ddd4jMQProperties props = new Ddd4jMQProperties();
            props.setEnabled(true);
            props.setNamespace("default-ns");
            return props;
        }

        @Bean
        MQListenerDefinitionRegistry mqListenerDefinitionRegistry() {
            return new MQListenerDefinitionRegistry();
        }

        @Bean
        MQListenerBeanPostProcessor mqListenerBeanPostProcessor(
                MQListenerDefinitionRegistry registry,
                Ddd4jMQProperties props) {
            return new MQListenerBeanPostProcessor(registry, props);
        }

        @Bean
        DemoListener demoListener() {
            return new DemoListener();
        }
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
