package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>event.setAggregateVersion()</code>\u3001<code>objectMapper.writeValueAsString()</code> \u9a8c\u8bc1 <code>DomainEventJson</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class DomainEventJsonTest {

    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>event.setAggregateVersion()</code>\u3001<code>objectMapper.writeValueAsString()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void shouldSerializeEventMetadataAsStableScalarValues() throws Exception {
        SampleDomainEvent event = new SampleDomainEvent("order-1");
        event.setAggregateVersion(new AggregateVersion(3));

        String json = objectMapper.writeValueAsString(event);

        assertThat(json)
                .contains("\"event-type\":\"SampleDomainEvent\"")
                .contains("\"event-id\":\"")
                .contains("\"entity-id-path\":\"String:order-1\"")
                .contains("\"aggregate-version\":3");
    }

    /** \u4f9b\u4f20\u8f93\u3001\u5e8f\u5217\u5316\u6216\u5904\u7406\u5668\u5206\u53d1\u9a8c\u8bc1\u7684\u9886\u57df\u4e8b\u4ef6\u6837\u672c\u3002 */
    private static final class SampleDomainEvent extends DomainEvent<StringEntityId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>SampleDomainEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param orderId \u6d4b\u8bd5\u4e2d\u8981\u5b9a\u4f4d\u7684\u8ba2\u5355\u6807\u8bc6
         */
        private SampleDomainEvent(String orderId) {
            super(orderId);
        }
    }
}
