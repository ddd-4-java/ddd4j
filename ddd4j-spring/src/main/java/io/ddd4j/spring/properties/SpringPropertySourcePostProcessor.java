package io.ddd4j.spring.properties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * Spring PropertySource 后处理器（从 ddd4j-core 迁出至 ddd4j-spring）
 * <p>
 * 原 ddd4j-core 的 {@code io.ddd4j.core.properties.BasePropertySourcePostProcessor} 已被删除。
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 3.4.x
 */
@Slf4j
public class SpringPropertySourcePostProcessor implements BeanFactoryPostProcessor, Ordered {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 加载默认的 classpath:ddd4j-default.properties
        try {
            Properties props = PropertiesLoaderUtils.loadAllProperties("ddd4j-default.properties");
            if (CollectionUtils.isEmpty(props)) {
                return;
            }
            ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
            MutablePropertySources sources = env.getPropertySources();
            PropertySource<?> propertySource = new PropertiesPropertySource("ddd4jDefault", props);
            sources.addLast(propertySource);
            log.debug("Loaded {} default properties into Spring environment", props.size());
        } catch (IOException e) {
            log.debug("No ddd4j-default.properties found, skipping");
        } catch (Exception e) {
            log.warn("Failed to load ddd4j default properties", e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
