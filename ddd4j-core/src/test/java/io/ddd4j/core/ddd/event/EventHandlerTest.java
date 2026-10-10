package io.ddd4j.core.ddd.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventHandlerTest {

    @Test
    void annotationIsRuntimeVisible() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderCreated", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);

        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertFalse(annotation.ignoreOnReplay(), "ignoreOnReplay should default to false");
    }

    @Test
    void ignoreOnReplayAttributeIsReadableAtRuntime() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderDiscarded", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);

        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertTrue(annotation.ignoreOnReplay(), "ignoreOnReplay should be readable when set to true");
    }

    @Test
    void annotationDeclarationIsMethodTargetedAndRuntimeRetained() {
        Target target = EventHandler.class.getAnnotation(Target.class);
        assertNotNull(target, "@EventHandler must declare @Target");
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, target.value(),
                "@EventHandler must target methods only");

        Retention retention = EventHandler.class.getAnnotation(Retention.class);
        assertNotNull(retention, "@EventHandler must declare @Retention");
        assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                "@EventHandler must be visible at runtime for reflective dispatch");
    }

    static class SampleHandler {

        @EventHandler
        public void onOrderCreated(OrderCreatedEvent event) {
        }

        @EventHandler(ignoreOnReplay = true)
        public void onOrderDiscarded(OrderCreatedEvent event) {
        }
    }

    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {

        OrderCreatedEvent() {
            super(new EntityIdPath(new OrderId("order-1")));
        }

        final static class OrderId implements EntityId {

            private static final long serialVersionUID = 0L;

            private final String value;

            private static final EntityType TYPE = new StringEntityType("Order");

            @Override
            public EntityType getType() {
                return TYPE;
            }

            @Override
            public String asString() {
                return value;
            }

            @Override
            public String asTypedString() {
                return TYPE.asString() + ":" + value;
            }

            @JsonCreator()
            OrderId(@JsonProperty("value") String value) {
                this.value = value;
            }

            @JsonProperty("value")
            public String value() {
                return value;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                    return false;
                }
                OrderId other = (OrderId) obj;
                return Objects.equals(this.value, other.value);
            }

            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(value);
                return result;
            }

            @Override
            public String toString() {
                return "OrderId[value=" + value + "]";
            }
        }
    }
}
