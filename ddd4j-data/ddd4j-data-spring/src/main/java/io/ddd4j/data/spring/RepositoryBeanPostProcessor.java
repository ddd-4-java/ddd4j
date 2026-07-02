package io.ddd4j.data.spring;

import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.data.mybatis.config.BaseDataProperties;
import io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * ddd4j Repository Spring 桥接：扫描 {@link BaseRepositoryImpl} 子类 Bean，
 * 自动注入 mapper 和 {@link BaseDataProperties}，并注册到 {@link RepositoryRegistry}。
 *
 * <p>承担 ddd4j-data-mybatis 与 Spring 容器的桥接职责。
 * BaseRepositoryImpl 本身保持纯 Java（setter 注入），Spring 注解集中在此桥接类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class RepositoryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BaseRepositoryImpl<?, ?, ?, ?> repositoryImpl) {
            // 注入 BaseDataProperties
            try {
                BaseDataProperties properties = applicationContext.getBean(BaseDataProperties.class);
                repositoryImpl.setBaseDataProperties(properties);
            } catch (Exception e) {
                log.debug("BaseDataProperties not available, skip injection: {}", e.getMessage());
            }

            // 注册到框架无关 RepositoryRegistry，供 AggregateRoot / Query 充血方法查找。
            try {
                Class<?> modelClass = (Class<?>) ReflectionKit.getSuperClassGenericType(bean.getClass(), BaseRepositoryImpl.class, 1);
                registerRepository(modelClass, repositoryImpl);
                log.debug("Registered Repository: {} → {}", modelClass.getSimpleName(), bean.getClass().getSimpleName());
            } catch (Exception e) {
                log.debug("Skip repository registration for {}: {}", beanName, e.getMessage());
            }
        }
        return bean;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRepository(Class<?> modelClass, Repository<?, ?> repository) {
        RepositoryRegistry.register((Class) modelClass, (Repository) repository);
    }

}
