/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.vertx;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * 仅限当前 Vert.x 实例的领域事件消息编解码器。
 *
 * <p>领域事件保持对象形态在本地 EventBus 中传递，不允许把该 codec 用于集群或跨进程传输。
 */
final class LocalEventMessageCodec implements MessageCodec<Object, Object> {

    /**
     * Vert.x 活动事件总线中注册的编解码器名。
     */
    private final String name;

    /**
     * 用独立名称登记本地事件总线编解码器。
     *
     * @param name 当前组件、指标、任务或 Bean 使用的名称
     */
    LocalEventMessageCodec(String name) {
        this.name = name;
    }

    /**
     * 拒绝将仅支持 Vert.x 本地传递的事件编码到网络。
     *
     * @param buffer 原生事件总线传输编码缓冲区
     * @param body   待传递的本地事件对象
     * @throws UnsupportedOperationException 本地事件无法编码到跨进程传输
     */
    @Override
    public void encodeToWire(Buffer buffer, Object body) {
        throw new UnsupportedOperationException("Local event codec does not support wire encoding");
    }

    /**
     * 拒绝从集群传输缓冲区读取本地编解码器管理的事件。
     *
     * @param position 从缓冲区读取数据的起始偏移
     * @param buffer   原生事件总线传输编码缓冲区
     * @return 本实现没有可从线路读取的结果
     * @throws UnsupportedOperationException 本地编解码器不能接收跨进程消息
     */
    @Override
    public Object decodeFromWire(int position, Buffer buffer) {
        throw new UnsupportedOperationException("Local event codec does not support wire decoding");
    }

    /**
     * 在本地总线中将消息对象原样传给消费者。
     *
     * @param body 待传递的本地事件对象
     * @return 当前事件对象
     */
    @Override
    public Object transform(Object body) {
        return body;
    }

    /**
     * 读取<code>name</code> 字段。
     *
     * @return <code>name</code> 字段
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * 声明编解码器是 Vert.x 自定义编码类型。
     *
     * @return 自定义编解码器的标识值
     */
    @Override
    public byte systemCodecID() {
        return -1;
    }
}
