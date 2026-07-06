package io.ddd4j.mq.listener;

import io.ddd4j.mq.annotation.MQEventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link MQEventListener} 监听器定义注册表，由 {@code io.ddd4j.mq.spring.registry.MQListenerBeanPostProcessor} 在 Bean 初始化阶段填充。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class ListenerDefinitionRegistry {

    private final List<ListenerDefinition> definitions = new CopyOnWriteArrayList<>();

    /**
     * 登记监听器定义（BeanPostProcessor 阶段调用）。
     *
     * @param definition 监听器定义
     */
    public void register(ListenerDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        definitions.add(definition);
        log.debug("Registered @MQEventListener: bean={}, method={}, topic={}",
                Objects.nonNull(definition.getBeanName()) ? definition.getBeanName() : definition.getBean().getClass().getSimpleName(),
                definition.getMethod().getName(),
                definition.getTopic());
    }

    /**
     * 返回已登记的监听器定义（不可变快照）。
     *
     * @return 监听器定义列表
     */
    public List<ListenerDefinition> getDefinitions() {
        return List.copyOf(definitions);
    }

    /**
     * 返回已登记监听器数量。
     */
    public int size() {
        return definitions.size();
    }
}
