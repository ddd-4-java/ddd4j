package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.listener.MQListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Objects;

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
 * <p>整体 try/catch 不中断应用启动（与 base-mq 行为一致），单个 broker 失败仅记录日志。
 * 注意：仅处理根上下文事件，避免父子容器重复装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerRegistrar {

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
                log.error("Initialize MQ client [{}] failed", client.impl(), ex);
            }
        }
        log.info("ddd4j-mq assembly completed: {} listener(s) registered, {} client(s) available",
                total, mqClients.size());
    }
}
