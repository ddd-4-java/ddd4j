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

    private final MQListenerBeanPostProcessor beanPostProcessor;
    private final List<MQClient> mqClients;
    private final MQProperties properties;
    private final MQEventSerialization serialization;
    private final ObjectProvider<MQEventStorer<?>> storerProvider;

    /**
     * 上下文就绪后装配所有 {@link MQClient}。
     *
     * @param event Spring 上下文刷新完成事件
     */
    @EventListener
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

        @SuppressWarnings("rawtypes")
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
