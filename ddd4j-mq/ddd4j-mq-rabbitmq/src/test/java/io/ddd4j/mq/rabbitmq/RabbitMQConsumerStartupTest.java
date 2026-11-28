package io.ddd4j.mq.rabbitmq;

import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.lifecycle.MQInitializationException;
import io.ddd4j.mq.lifecycle.MQStartupState;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 原生订阅失败必须进入 MQ 启动状态机，并按监听器要求回滚相应资源。
 */
class RabbitMQConsumerStartupTest {
    private Connection connection;
    private Channel producer;
    private Channel started;
    private Channel failed;
    private Channel later;
    private RabbitMQClient client;
    private RabbitMQProperties properties;
    private final IOException subscriptionFailure = new IOException("subscription denied");

    @BeforeEach
    void setUp() throws Exception {
        connection = mock(Connection.class);
        producer = mock(Channel.class);
        started = mock(Channel.class);
        failed = mock(Channel.class);
        later = mock(Channel.class);
        for (Channel channel : Arrays.asList(producer, started, failed, later)) {
            when(channel.isOpen()).thenReturn(true);
        }
        when(connection.createChannel()).thenReturn(producer, started, failed, later);
        when(failed.basicConsume(anyString(), eq(false), any(DeliverCallback.class), any(CancelCallback.class)))
                .thenThrow(subscriptionFailure);
        properties = new RabbitMQProperties();
        properties.setEnabled(true);
        properties.setBroker("rabbit");
        properties.setExchange("events");
        properties.setAutoAck(false);
        properties.setProducerChannelPoolSize(1);
        client = new RabbitMQClient(connection);
    }

    @AfterEach
    void tearDown() {
        try {
            client.close();
        } finally {
            BaseContext.clear();
        }
    }

    @Test
    void requiredSubscriptionFailureMustFailStartupAndRollbackEarlierResources() throws Exception {
        List<MQListener> listeners = Arrays.asList(listener("first", true), listener("failed", true),
                listener("later", true));

        MQInitializationException exception = assertThrows(MQInitializationException.class,
                () -> client.init(listeners, properties, mock(MQEventSerialization.class), null));

        assertSame(subscriptionFailure, exception.getCause());
        assertEquals(MQStartupState.FAILED, client.startupStatus().snapshot().state());
        assertEquals(1, client.startupStatus().snapshot().failures().size());
        assertNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
        verify(producer).close();
        verify(started).close();
        verify(failed).close();
        verify(connection, times(3)).createChannel();
        verify(connection, never()).close();
    }

    @Test
    void optionalSubscriptionFailureMustDegradeAndContinueOtherListeners() throws Exception {
        List<MQListener> listeners = Arrays.asList(listener("first", true), listener("failed", false),
                listener("later", true));

        client.init(listeners, properties, mock(MQEventSerialization.class), null);

        assertEquals(MQStartupState.DEGRADED, client.startupStatus().snapshot().state());
        assertEquals(1, client.startupStatus().snapshot().failures().size());
        assertFalse(client.startupStatus().snapshot().failures().get(0).required());
        assertNotNull(BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER));
        verify(failed).close();
        verify(producer, never()).close();
        verify(started, never()).close();
        verify(later).basicConsume(anyString(), eq(false), any(DeliverCallback.class), any(CancelCallback.class));
        verify(connection, never()).close();
    }

    private static MQListener listener(String group, boolean required) throws Exception {
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        MQListener listener = MQListener.of(new Handler(), method, method.getAnnotation(MQEventListener.class));
        listener.setGroup(group);
        listener.setRequired(required);
        return listener;
    }

    /**
     * 本用例只注册订阅，业务消费由适配器往返测试覆盖。
     */
    public static final class Handler {
        @MQEventListener(topic = "orders", tags = "*")
        public void handle(MQEvent event) {
        }
    }
}
