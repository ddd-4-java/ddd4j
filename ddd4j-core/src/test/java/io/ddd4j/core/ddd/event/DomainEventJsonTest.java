package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventJsonTest {

    // Jackson 2 不会自动注册 java.time 支持；findAndAddModules 经 SPI 发现 classpath 上的 jsr310（JavaTimeModule）
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

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

    private static final class SampleDomainEvent extends DomainEvent<StringEntityId> {

        private SampleDomainEvent(String orderId) {
            super(orderId);
        }
    }
}
