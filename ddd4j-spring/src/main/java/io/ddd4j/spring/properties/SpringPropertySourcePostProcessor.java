package io.ddd4j.spring.properties;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * Spring 属性源后置处理器。
 *
 * <p>将 ddd4j 框架级别的配置属性源（如 application-ddd4j.yml）注入到 Spring 环境。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SpringPropertySourcePostProcessor implements BeanFactoryPostProcessor, Ordered {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // 将 ddd4j 配置属性源添加到 Spring Environment
        if (beanFactory instanceof ConfigurableEnvironment env) {
            env.getPropertySources()
                    .addLast(new PropertySource<>("ddd4j-defaults", System.getProperties()) {
                        @Override
                        public Object getProperty(String name) {
                            // 仅处理 ddd4j.* 前缀属性
                            if (name != null && name.startsWith("ddd4j.")) {
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
