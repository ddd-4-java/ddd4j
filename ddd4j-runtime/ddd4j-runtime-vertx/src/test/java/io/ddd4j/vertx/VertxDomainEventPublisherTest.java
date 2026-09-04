package io.ddd4j.vertx;

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

    private record LocalEvent(String name) {
    }
}
