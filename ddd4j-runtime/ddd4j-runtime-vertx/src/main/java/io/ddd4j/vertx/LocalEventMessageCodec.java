package io.ddd4j.vertx;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * 仅限当前 Vert.x 实例的领域事件消息编解码器。
 *
 * <p>领域事件保持对象形态在本地 EventBus 中传递，不允许把该 codec 用于集群或跨进程传输。
 */
final class LocalEventMessageCodec implements MessageCodec<Object, Object> {

    /** Vert.x \u6d3b\u52a8\u4e8b\u4ef6\u603b\u7ebf\u4e2d\u6ce8\u518c\u7684\u7f16\u89e3\u7801\u5668\u540d\u3002 */
    private final String name;

    /**
     * \u7528\u72ec\u7acb\u540d\u79f0\u767b\u8bb0\u672c\u5730\u4e8b\u4ef6\u603b\u7ebf\u7f16\u89e3\u7801\u5668\u3002
     * @param name \u5f53\u524d\u7ec4\u4ef6\u3001\u6307\u6807\u3001\u4efb\u52a1\u6216 Bean \u4f7f\u7528\u7684\u540d\u79f0
     */
    LocalEventMessageCodec(String name) {
        this.name = name;
    }

    /**
     * \u62d2\u7edd\u5c06\u4ec5\u652f\u6301 Vert.x \u672c\u5730\u4f20\u9012\u7684\u4e8b\u4ef6\u7f16\u7801\u5230\u7f51\u7edc\u3002
     * @param buffer \u539f\u751f\u4e8b\u4ef6\u603b\u7ebf\u4f20\u8f93\u7f16\u7801\u7f13\u51b2\u533a
     * @param body \u5f85\u4f20\u9012\u7684\u672c\u5730\u4e8b\u4ef6\u5bf9\u8c61
     * @throws UnsupportedOperationException \u672c\u5730\u4e8b\u4ef6\u65e0\u6cd5\u7f16\u7801\u5230\u8de8\u8fdb\u7a0b\u4f20\u8f93
     */
    @Override
    public void encodeToWire(Buffer buffer, Object body) {
        throw new UnsupportedOperationException("Local event codec does not support wire encoding");
    }

    /**
     * \u62d2\u7edd\u4ece\u96c6\u7fa4\u4f20\u8f93\u7f13\u51b2\u533a\u8bfb\u53d6\u672c\u5730\u7f16\u89e3\u7801\u5668\u7ba1\u7406\u7684\u4e8b\u4ef6\u3002
     * @param position \u4ece\u7f13\u51b2\u533a\u8bfb\u53d6\u6570\u636e\u7684\u8d77\u59cb\u504f\u79fb
     * @param buffer \u539f\u751f\u4e8b\u4ef6\u603b\u7ebf\u4f20\u8f93\u7f16\u7801\u7f13\u51b2\u533a
     * @return \u672c\u5b9e\u73b0\u6ca1\u6709\u53ef\u4ece\u7ebf\u8def\u8bfb\u53d6\u7684\u7ed3\u679c
     * @throws UnsupportedOperationException \u672c\u5730\u7f16\u89e3\u7801\u5668\u4e0d\u80fd\u63a5\u6536\u8de8\u8fdb\u7a0b\u6d88\u606f
     */
    @Override
    public Object decodeFromWire(int position, Buffer buffer) {
        throw new UnsupportedOperationException("Local event codec does not support wire decoding");
    }

    /**
     * \u5728\u672c\u5730\u603b\u7ebf\u4e2d\u5c06\u6d88\u606f\u5bf9\u8c61\u539f\u6837\u4f20\u7ed9\u6d88\u8d39\u8005\u3002
     * @param body \u5f85\u4f20\u9012\u7684\u672c\u5730\u4e8b\u4ef6\u5bf9\u8c61
     * @return \u5f53\u524d\u4e8b\u4ef6\u5bf9\u8c61
     */
    @Override
    public Object transform(Object body) {
        return body;
    }

    /**
     * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
     * @return <code>name</code> \u5b57\u6bb5
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * \u58f0\u660e\u7f16\u89e3\u7801\u5668\u662f Vert.x \u81ea\u5b9a\u4e49\u7f16\u7801\u7c7b\u578b\u3002
     * @return \u81ea\u5b9a\u4e49\u7f16\u89e3\u7801\u5668\u7684\u6807\u8bc6\u503c
     */
    @Override
    public byte systemCodecID() {
        return -1;
    }
}
