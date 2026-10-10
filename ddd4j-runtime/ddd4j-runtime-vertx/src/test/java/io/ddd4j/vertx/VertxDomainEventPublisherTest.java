package io.ddd4j.vertx;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VertxDomainEventPublisherTest {

    @Test
    void shouldPublishLocalEventWithoutRequiringWireCodec() throws Exception {
        Vertx vertx = Vertx.vertx();
        try {
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<Object> body = new AtomicReference<>();
            vertx.eventBus().consumer(VertxDomainEventPublisher.ADDRESS, message -> {
                body.set(message.body());
                received.countDown();
            });

            Object event = new LocalEvent("order-created");
            new VertxDomainEventPublisher(vertx).publish(event);

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(body.get()).isSameAs(event);
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    private final static class LocalEvent {

        private static final long serialVersionUID = 0L;

        private final String name;

        @JsonCreator()
        private LocalEvent(@JsonProperty("name") String name) {
            this.name = name;
        }

        @JsonProperty("name")
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            LocalEvent other = (LocalEvent) obj;
            return Objects.equals(this.name, other.name);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            return result;
        }

        @Override
        public String toString() {
            return "LocalEvent[name=" + name + "]";
        }
    }
}
