package io.ddd4j.spring.ddd.scanner;

import io.ddd4j.annotation.ddd.*;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;

/**
 * DDD 注解扫描器：让 Spring 自动识别纯 Java 的 DDD 构造型注解。
 *
 * <p>扫描 {@link DomainService}、{@link DomainRepository}、{@link ApplicationService}、
 * {@link DomainAssembler}、{@link DomainConverter} 注解标记的类，并注册为 Spring Bean。
 *
 * <p>使用方式：在 Spring 配置类中调用
 * <pre>
 * DddClassPathBeanDefinitionScanner scanner = new DddClassPathBeanDefinitionScanner(registry);
 * scanner.scan("com.example.domain");
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class DddClassPathBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {

    public DddClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
        registerDddFilters();
    }

    /**
     * 注册 DDD 构造型注解过滤器。
     *
     * <p>Spring 的 {@code ClassPathBeanDefinitionScanner} 默认只识别
     * {@code @Component}（含 {@code @Service}、{@code @Repository} 等元注解）。
     * 这里额外注册 DDD 注解过滤器，使纯 Java 的 DDD 注解也能被扫描。
     */
    protected void registerDddFilters() {
        addIncludeFilter(new AnnotationTypeFilter(DomainService.class));
        addIncludeFilter(new AnnotationTypeFilter(DomainRepository.class));
        addIncludeFilter(new AnnotationTypeFilter(DomainGateway.class));
        addIncludeFilter(new AnnotationTypeFilter(ApplicationService.class));
        addIncludeFilter(new AnnotationTypeFilter(CommandExecutor.class));
        addIncludeFilter(new AnnotationTypeFilter(QueryService.class));
        addIncludeFilter(new AnnotationTypeFilter(DomainAssembler.class));
        addIncludeFilter(new AnnotationTypeFilter(DomainConverter.class));
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        // 允许非独立的注解（如接口的代理）也能被注册
        return beanDefinition.getMetadata().isIndependent();
    }

    @Override
    public int scan(String... basePackages) {
        int beanCount = super.scan(basePackages);
        if (logger.isDebugEnabled()) {
            logger.debug("DddClassPathBeanDefinitionScanner scanned " + beanCount
                    + " DDD-annotated beans from packages: " + String.join(", ", basePackages));
        }
        return beanCount;
    }

    /**
     * 扫描并注册 DDD Bean，返回注册的 BeanDefinition 集合。
     */
    public Set<BeanDefinitionHolder> scanAndRegister(String... basePackages) {
        return doScan(basePackages);
    }
}
