package io.ddd4j.mq.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * {@link EventListener} 监听器定义访问门面。
 * <p>
 * 定义由下游框架适配层（如 ddd4j-mq-spring 的 BeanPostProcessor）在容器初始化阶段写入 {@link ListenerDefinitionRegistry}，
 * 本类仅提供只读访问。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class ListenerScanner {

    private final ListenerDefinitionRegistry registry;

    /**
     * 校验监听器定义非空且方法可访问。
     *
     * @param definition 监听器定义
     */
    public static void prepareMethod(ListenerDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Method method = definition.getMethod();
        Object bean = definition.getBean();
        if (Objects.nonNull(bean) && !method.canAccess(bean)) {
            method.setAccessible(true);
        }
    }

    /**
     * 返回容器初始化阶段已登记的监听器定义。
     *
     * @return 不可变监听器定义列表
     */
    public List<ListenerDefinition> scan() {
        List<ListenerDefinition> definitions = registry.getDefinitions();
        log.info("Resolved {} @EventListener definition(s) from registry", definitions.size());
        return definitions;
    }
}
