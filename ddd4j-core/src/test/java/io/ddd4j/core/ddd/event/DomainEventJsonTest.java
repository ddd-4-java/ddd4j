package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventJsonTest {

    // Jackson 3 内建 JavaTimeModule（自动注册），无需手动 registerModule
    private final ObjectMapper objectMapper = new ObjectMapper();

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
