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

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>MQListenerClasspathScanner</code> \u5b9e\u4f8b\u3002 */
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
     * \u626b\u63cf\u5355\u4e2a\u7c7b\u4e0a\u7684 {@link MQEventListener} \u65b9\u6cd5\u3002
     *
     * @param beanName Bean \u540d\u79f0
     * @param beanClass \u5f85\u626b\u63cf\u7684 Bean \u7c7b\u578b
     * @param consumer \u53d1\u73b0\u76d1\u542c\u5668\u65b9\u6cd5\u540e\u7684\u56de\u8c03
     * @return \u5df2\u53d1\u73b0\u7684\u76d1\u542c\u5668\u65b9\u6cd5
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
     * \u4ece BeanDefinition \u89e3\u6790 Class\u3002
     *
     * @param beanDefinition Bean \u5b9a\u4e49
     * @param classLoader \u7528\u4e8e\u52a0\u8f7d Bean \u7c7b\u578b\u7684\u7c7b\u52a0\u8f7d\u5668
     * @return \u89e3\u6790\u51fa\u7684 Bean \u7c7b\u578b
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

    /**
     * \u68c0\u67e5 Bean \u7c7b\u578b\u662f\u5426\u5c5e\u4e8e\u5e94\u8df3\u8fc7\u4e1a\u52a1\u76d1\u542c\u626b\u63cf\u7684\u57fa\u7840\u8bbe\u65bd\u3002
     * @param clazz \u5f85\u5224\u65ad\u662f\u5426\u4e3a\u57fa\u7840\u8bbe\u65bd\u7684 Bean \u7c7b\u578b
     * @return \u4e0d\u5e94\u626b\u63cf\u8be5\u7c7b\u7684\u76d1\u542c\u65b9\u6cd5\u65f6\u8fd4\u56de true
     */
    private static boolean isInfrastructureClass(Class<?> clazz) {
        String name = clazz.getName();
        return name.startsWith("org.springframework")
                || name.startsWith("java.")
                || name.startsWith("jakarta.");
    }
}
