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
package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.listener.MQListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用上下文就绪后驱动 {@link MQClient} 装配的桥接器（对标 base-mq {@code BaseMQConfig}）。
 * 本模块不承载 broker 消息，不负责读写 {@code ddd4j-message-id}；该职责属于实际 MQ adapter。
 *
 * <p>在 {@link ContextRefreshedEvent}（所有 Bean 初始化完成后）触发，把
 * {@link MQListenerBeanPostProcessor} 收集的监听器列表连同配置/序列化器/持久化器
 * 传给每个 {@link MQClient}：
 * <ol>
 *   <li>{@link MQClient#init} 内部按 {@code properties.broker == client.impl()} 短路，
 *       只激活与配置匹配的 broker（装配层无需自行选择）</li>
 *   <li>{@link MQClient#init} 注册 producer/consumer 到 {@code BaseContext}，
 *       {@link MQListenerBeanPostProcessor} 扫描到的 {@code @MQEventListener} 方法在此被消费</li>
 *   <li>{@link MQClient#start} 统一启动各 broker 的消费线程</li>
 * </ol>
 *
 * <p>客户端初始化或启动失败时立即终止 Spring 上下文启动，并关闭已经创建的 MQ 资源。
 * 注意：仅处理根上下文事件，避免父子容器重复装配；销毁操作幂等且按客户端注册顺序逆序执行。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerRegistrar implements DisposableBean {

    /** \u6355\u83b7\u5bb9\u5668\u4e2d\u5e26\u6d88\u606f\u76d1\u542c\u6ce8\u89e3\u65b9\u6cd5\u7684\u540e\u5904\u7406\u5668\u3002 */
    private final MQListenerBeanPostProcessor beanPostProcessor;
    /** \u5f53\u524d Spring \u5bb9\u5668\u6301\u6709\u7684\u6d88\u606f\u4ee3\u7406\u9002\u914d\u5668\u3002 */
    private final List<MQClient> mqClients;
    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final MQProperties properties;
    /** \u7528\u4e8e\u8bfb\u5199\u6d88\u606f\u8d1f\u8f7d\u7684\u534f\u8bae\u7f16\u7801\u7ec4\u4ef6\u3002 */
    private final MQEventSerialization serialization;
    /** \u6309\u9700\u63d0\u4f9b\u6d88\u606f\u5904\u7406\u72b6\u6001\u5b58\u50a8\u7684\u7ec4\u4ef6\u3002 */
    private final ObjectProvider<MQEventStorer<?>> storerProvider;
    /** \u5f53\u524d Spring \u6d88\u606f\u76d1\u542c\u6ce8\u518c\u5668\u662f\u5426\u5df2\u5173\u95ed\u3002 */
    private final AtomicBoolean destroyed = new AtomicBoolean();

    /**
     * 上下文就绪后装配所有 {@link MQClient}。
     *
     * @param event Spring 上下文刷新完成事件
     */
    @EventListener
    @SuppressWarnings("rawtypes")
    public void onContextRefreshed(ContextRefreshedEvent event) {
        // 仅处理根上下文，避免 MVC/WebFlux 等父子容器重复触发
        if (Objects.nonNull(event.getApplicationContext().getParent())) {
            return;
        }
        if (Objects.isNull(mqClients) || mqClients.isEmpty()) {
            log.debug("No MQClient bean found, MQ assembly skipped");
            return;
        }

        List<MQListener> listeners = beanPostProcessor.getListeners();
        if (!properties.isEnabled()) {
            log.debug("ddd4j.mq.enabled=false, MQ assembly skipped");
            return;
        }

        MQEventStorer storer = storerProvider.getIfAvailable();
        int total = listeners.size();

        for (MQClient client : mqClients) {
            try {
                client.init(listeners, properties, serialization, storer);
                client.start();
            } catch (Exception ex) {
                ApplicationContextException startupFailure = new ApplicationContextException(
                        "Initialize MQ client [" + client.impl() + "] failed", ex);
                try {
                    destroy();
                } catch (Exception closeFailure) {
                    startupFailure.addSuppressed(closeFailure);
                }
                throw startupFailure;
            }
        }
        log.info("ddd4j-mq assembly completed: {} listener(s) registered, {} client(s) available",
                total, mqClients.size());
    }

    /**
     * \u9006\u5e8f\u5173\u95ed\u6240\u6709\u5ba2\u6237\u7aef\u3002\u5355\u4e2a\u5ba2\u6237\u7aef\u5173\u95ed\u5931\u8d25\u4e0d\u4f1a\u963b\u6b62\u5176\u4f59\u5ba2\u6237\u7aef\u91ca\u653e\u8d44\u6e90\u3002
     *
     * @throws Exception \u5173\u95ed\u5df2\u6ce8\u518c\u7684\u6d88\u606f\u76d1\u542c\u7ec4\u4ef6\u5931\u8d25
     */
    @Override
    public void destroy() throws Exception {
        if (!destroyed.compareAndSet(false, true) || Objects.isNull(mqClients)) {
            return;
        }
        Exception aggregate = null;
        for (int index = mqClients.size() - 1; index >= 0; index--) {
            MQClient client = mqClients.get(index);
            try {
                client.close();
            } catch (Exception closeFailure) {
                if (Objects.isNull(aggregate)) {
                    aggregate = new IllegalStateException("Close MQ clients failed");
                }
                aggregate.addSuppressed(closeFailure);
            }
        }
        if (Objects.nonNull(aggregate)) {
            throw aggregate;
        }
    }
}
