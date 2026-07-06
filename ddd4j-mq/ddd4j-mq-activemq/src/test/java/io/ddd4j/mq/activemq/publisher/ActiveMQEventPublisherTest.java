package io.ddd4j.mq.activemq.publisher;

import io.ddd4j.mq.message.Destination;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class ActiveEventPublisherTest {

    @Test
    void resolveDestinationShouldBuildTopicByDefault() throws Exception {
        Session session = mock(Session.class);
        Topic topic = mock(Topic.class);
        when(session.createTopic("sales.order.paid")).thenReturn(topic);

        assertSame(topic, ActiveMQEventPublisher.resolveDestination(session, Destination.of("order", "paid", "sales")));

        verify(session).createTopic("sales.order.paid");
    }

    @Test
    void resolveDestinationShouldHonorQueuePrefix() throws Exception {
        Session session = mock(Session.class);
        Queue queue = mock(Queue.class);
        when(session.createQueue("jobs.high")).thenReturn(queue);

        assertSame(queue, ActiveMQEventPublisher.resolveDestination(session, Destination.of("queue:jobs", "high")));

        verify(session).createQueue("jobs.high");
    }
}
