package io.ddd4j.spring.properties;

import java.util.Objects;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * 基础属性源后置处理器。
 *
 * <p>将应用配置属性（如 application.yml 中的 ddd4j.* 配置）注入到 Spring Environment，
 * 供 {@code ddd4j-webmvc} 等模块通过 {@code @Value("${ddd4j.xxx}")} 读取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class BasePropertySourcePostProcessor implements BeanFactoryPostProcessor, Ordered {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory instanceof ConfigurableEnvironment env) {
            env.getPropertySources()
                    .addLast(new PropertySource<>("ddd4j-app-props", System.getProperties()) {
                        @Override
                        public Object getProperty(String name) {
                            if (Objects.nonNull(name) && name.startsWith("ddd4j.")) {
                                return System.getProperty(name);
                            }
                            return null;
                        }
                    });
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
