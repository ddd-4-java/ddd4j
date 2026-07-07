package io.ddd4j.mq.spring.registry;

import io.ddd4j.mq.annotation.MQEventListener;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Bean 定义阶段的 {@link MQEventListener} 类路径扫描器（从 ddd4j-mq-core 迁出）。
 * <p>
 * 供 Cloud {@code FunctionalConsumerRegistrar} 等在 BeanPostProcessor 之前注册 Stream 函数 Bean 时复用，
 * 与运行时 {@link MQListenerBeanPostProcessor} 形成「早期 BFPP + 晚期 BPP」双阶段发现模型。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class MQListenerClasspathScanner {

    private MQListenerClasspathScanner() {
    }

    /**
     * 扫描 Bean 定义注册表中的用户 Bean 类，回调每个 {@link MQEventListener} 方法。
     *
     * @param beanNames            Bean 名称列表
     * @param beanDefinitionLookup BeanDefinition 查找函数
     * @param classLoader          类加载器
     * @param consumer             回调 (beanName, annotatedMethod)
     */
    public static void scanBeanDefinitions(
            Iterable<String> beanNames,
            Function<String, BeanDefinition> beanDefinitionLookup,
            ClassLoader classLoader,
            BiConsumer<String, Method> consumer) {

        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = beanDefinitionLookup.apply(beanName);
            if (Objects.isNull(beanDefinition)) {
                continue;
            }
            Class<?> beanClass = resolveBeanClass(beanDefinition, classLoader);
            if (Objects.isNull(beanClass) || isInfrastructureClass(beanClass)) {
                continue;
            }
            scanClass(beanName, beanClass, consumer);
        }
    }

    /**
     * 扫描单个类上的 {@link MQEventListener} 方法。
     */
    public static List<Method> scanClass(String beanName, Class<?> beanClass, BiConsumer<String, Method> consumer) {
        List<Method> found = new ArrayList<>();
        for (Method method : beanClass.getDeclaredMethods()) {
            MQEventListener annotation = AnnotationUtils.findAnnotation(method, MQEventListener.class);
            if (Objects.isNull(annotation)) {
                continue;
            }
            found.add(method);
            if (Objects.nonNull(consumer)) {
                consumer.accept(beanName, method);
            }
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * 从 BeanDefinition 解析 Class。
     */
    public static Class<?> resolveBeanClass(BeanDefinition beanDefinition, ClassLoader classLoader) {
        String className = beanDefinition.getBeanClassName();
        if (!StringUtils.hasText(className) && Objects.nonNull(beanDefinition.getResolvableType())) {
            Class<?> resolved = beanDefinition.getResolvableType().resolve();
            if (Objects.nonNull(resolved)) {
                return resolved;
            }
        }
        if (!StringUtils.hasText(className)) {
            return null;
        }
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static boolean isInfrastructureClass(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("org.springframework")
                || name.startsWith("java.")
                || name.startsWith("jakarta.");
    }
}
