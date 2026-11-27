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

/** \u9a8c\u8bc1 <code>VertxDomainEventPublisher</code> \u5728<code>shouldPublishLocalEvent</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class VertxDomainEventPublisherTest {

    /**
     * \u9a8c\u8bc1<code>received.await(5, TimeUnit.SECONDS)</code>\u4e3a\u771f\uff0c<code>body.get()</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>event</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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

    /** \u5c06 <code>name</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private final static class LocalEvent {

        /** Java 序列化版本标识。 */
        private static final long serialVersionUID = 0L;

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;

        /**
         * \u8bbe\u7f6e <code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>LocalEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        @JsonCreator()
        private LocalEvent(@JsonProperty("name") String name) {
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
         */
        @JsonProperty("name")
        public String name() {
            return name;
        }

        /**
         * 执行 <code>equals</code>，调用 <code>obj.getClass</code>。
         *
         * @param obj 传入此操作的 <code>Object</code> 类型 <code>obj</code>
         * @return 按条件返回 <code>true</code>、<code>false</code>、<code>Objects.equals(this.name, other.name)</code>
         */
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

        /**
         * 读取<code>result</code> 的当前值。
         *
         * @return 返回表达式 <code>result</code>
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            return result;
        }

        /**
         * 按 <code>to String</code> 的契约计算并返回结果。
         *
         * @return 返回表达式 <code>"LocalEvent[name=" + name + "]"</code>
         */
        @Override
        public String toString() {
            return "LocalEvent[name=" + name + "]";
        }
    }
}
