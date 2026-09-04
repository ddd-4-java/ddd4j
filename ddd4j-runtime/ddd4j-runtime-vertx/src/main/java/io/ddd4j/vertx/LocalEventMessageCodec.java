package io.ddd4j.vertx;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * 仅限当前 Vert.x 实例的领域事件消息编解码器。
 *
 * <p>领域事件保持对象形态在本地 EventBus 中传递，不允许把该 codec 用于集群或跨进程传输。
 */
final class LocalEventMessageCodec implements MessageCodec<Object, Object> {

    private final String name;

    LocalEventMessageCodec(String name) {
        this.name = name;
    }

    @Override
    public void encodeToWire(Buffer buffer, Object body) {
        throw new UnsupportedOperationException("Local event codec does not support wire encoding");
    }

    @Override
    public Object decodeFromWire(int position, Buffer buffer) {
        throw new UnsupportedOperationException("Local event codec does not support wire decoding");
    }

    @Override
    public Object transform(Object body) {
        return body;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
