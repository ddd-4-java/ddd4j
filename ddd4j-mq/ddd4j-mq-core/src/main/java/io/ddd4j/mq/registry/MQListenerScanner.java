package io.ddd4j.mq.registry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * {@link MQEventListener} 监听器定义访问门面。
 * <p>
 * 定义由 {@code io.ddd4j.mq.spring.registry.MQListenerBeanPostProcessor} 在 Bean 初始化阶段写入 {@link MQListenerDefinitionRegistry}，
 * 本类仅提供只读访问，替代 legacy 全容器遍历扫描。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerScanner {

    private final MQListenerDefinitionRegistry registry;

    /**
     * 校验监听器定义非空且方法可访问。
     *
     * @param definition 监听器定义
     */
    public static void prepareMethod(MQListenerDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Method method = definition.getMethod();
        Object bean = definition.getBean();
        if (bean != null && !method.canAccess(bean)) {
            method.setAccessible(true);
        }
    }

    /**
     * 返回 BeanPostProcessor 阶段已登记的监听器定义。
     *
     * @return 不可变监听器定义列表
     */
    public List<MQListenerDefinition> scan() {
        List<MQListenerDefinition> definitions = registry.getDefinitions();
        log.info("Resolved {} @MQEventListener definition(s) from registry", definitions.size());
        return definitions;
    }
}
