package io.ddd4j.mq;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.listener.MQListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 <code>MQClientPersistenceContract</code> 在 <code>persist</code> 等场景中的行为。
 */
class MQClientPersistenceContractTest {

    /**
     * 测试后依次清除<code>BaseContext</code>。
     */
    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    /**
     * 验证<code>client.consume(listener(invoked), event(), null)</code> 抛出 <code>IllegalStateException.class</code>，<code>invoked.get()</code>为假。
     *
     * @throws Throwable 读取测试数据或等待测试结果失败
     */
    @Test
    void persistShouldFailBeforeBusinessHandlerWhenStorerIsMissing() throws Throwable {
        AtomicBoolean invoked = new AtomicBoolean();
        MQClient client = client();
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), null);

        assertThrows(IllegalStateException.class,
                () -> client.consume(listener(invoked), event(), null));
        assertFalse(invoked.get());
    }

    /**
     * 验证<code>client.consume(listener(invoked), event(), null)</code> 抛出 <code>IllegalStateException.class</code>，<code>invoked.get()</code>为假。
     *
     * @throws Throwable 读取测试数据或等待测试结果失败
     */
    @Test
    void persistShouldFailBeforeBusinessHandlerWhenStoreFails() throws Throwable {
        AtomicBoolean invoked = new AtomicBoolean();
        MQClient client = client();
        MQProperties properties = properties();
        client.init(Collections.<MQListener>emptyList(), properties, serialization(), event -> {
            throw new IllegalStateException("store unavailable");
        });

        assertThrows(IllegalStateException.class,
                () -> client.consume(listener(invoked), event(), null));
        assertFalse(invoked.get());
    }

    /**
     * 构造 <code>MQClient</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>MQClient</code> 结果
     */
    private MQClient client() {
        return new MQClient() {
            @Override
            public String impl() {
                return "test";
            }

            @Override
            public Consumer<MQEvent> initProducer(MQProperties properties) {
                return null;
            }

            @Override
            public boolean initConsumer(MQListener listener, MQProperties properties) {
                return false;
            }
        };
    }

    /**
     * 读取 <code>properties</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>properties</code>
     */
    private MQProperties properties() {
        MQProperties properties = new MQProperties();
        properties.setEnabled(true);
        properties.setBroker("test");
        properties.setPersist(true);
        return properties;
    }

    /**
     * 构造 <code>MQEventSerialization</code> 作为当前测试的输入或结果。
     *
     * @return 提供给后续契约断言的 <code>MQEventSerialization</code> 结果
     */
    private MQEventSerialization serialization() {
        return new MQEventSerialization() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T serialize(Object event) {
                return (T) "{}";
            }

            @Override
            public <S, T> T deserialize(S value, Class<T> type) {
                return null;
            }
        };
    }

    /**
     * 调用 <code>Handler.class.getMethod()</code>、<code>MQListener.builder()</code>、<code>Collections.singletonList()</code> 执行当前夹具操作。
     *
     * @param invoked 本次调用使用的 <code>AtomicBoolean</code> 参数 <code>invoked</code>
     * @return 提供给后续契约断言的 <code>MQListener</code> 结果
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private MQListener listener(AtomicBoolean invoked) throws Exception {
        Handler handler = new Handler(invoked);
        Method method = Handler.class.getMethod("handle", MQEvent.class);
        return MQListener.builder().bean(handler).method(method).topic("orders").tags("*")
                .supports(Collections.singletonList("*")).group("test").namespace("").separator(".").build();
    }

    /**
     * 读取 <code>event</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>event</code>
     */
    private MQEvent event() {
        MQEvent event = new MQEvent();
        event.setTopic("orders");
        return event;
    }

    /**
     * 将 <code>invoked</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    public static final class Handler {
        /**
         * 记录受测执行器或监听器是否被调用。
         */
        private final AtomicBoolean invoked;

        /**
         * 设置 <code>invoked</code>，用于构造 <code>Handler</code> 测试实例。
         *
         * @param invoked 用于初始化 <code>Handler</code> 的 <code>AtomicBoolean</code> 值
         */
        Handler(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        /**
         * 调用 <code>invoked.set()</code> 执行当前夹具操作。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        public void handle(MQEvent event) {
            invoked.set(true);
        }
    }
}
