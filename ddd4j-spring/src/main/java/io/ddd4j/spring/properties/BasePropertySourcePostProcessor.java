package io.ddd4j.spring.properties;

import lombok.EqualsAndHashCode;
import io.ddd4j.core.properties.BasePropertySource;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自定义资源文件读取，优先级最低（纯 Spring Framework，不依赖 Spring Boot PropertySourceLoader）。
 */
@Slf4j
public class BasePropertySourcePostProcessor implements BeanFactoryPostProcessor, InitializingBean, Ordered {

    private final ResourceLoader resourceLoader;

    public BasePropertySourcePostProcessor() {
        this.resourceLoader = new DefaultResourceLoader();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        log.info("BasePropertySourcePostProcessor process @BasePropertySource bean.");
        Map<String, Object> beansWithAnnotation = beanFactory.getBeansWithAnnotation(BasePropertySource.class);
        Set<Map.Entry<String, Object>> beanEntrySet = beansWithAnnotation.entrySet();
        if (beanEntrySet.isEmpty()) {
            log.warn("Not found @BasePropertySource on spring bean class.");
            return;
        }
        List<PropertyFile> propertyFileList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : beanEntrySet) {
            Class<?> beanClass = ClassUtils.getUserClass(entry.getValue());
            BasePropertySource propertySource = AnnotationUtils.getAnnotation(beanClass, BasePropertySource.class);
            if (propertySource == null) {
                continue;
            }
            propertyFileList.add(new PropertyFile(propertySource.order(), propertySource.value(), propertySource.loadActiveProfile()));
        }

        List<PropertyFile> sortedPropertyList = propertyFileList.stream().distinct().sorted().collect(Collectors.toList());
        ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);
        MutablePropertySources propertySources = environment.getPropertySources();

        String[] activeProfiles = environment.getActiveProfiles();
        List<PropertySource<?>> propertySourceList = new ArrayList<>();
        for (String profile : activeProfiles) {
            for (PropertyFile propertyFile : sortedPropertyList) {
                if (!propertyFile.loadActiveProfile) {
                    continue;
                }
                String extension = propertyFile.getExtension();
                String location = propertyFile.getLocation();
                String filePath = StringUtils.stripFilenameExtension(location);
                String profiledLocation = filePath + "-" + profile + "." + extension;
                Resource resource = resourceLoader.getResource(profiledLocation);
                loadPropertySource(profiledLocation, resource, extension, propertySourceList);
            }
        }
        for (PropertyFile propertyFile : sortedPropertyList) {
            String extension = propertyFile.getExtension();
            String location = propertyFile.getLocation();
            Resource resource = resourceLoader.getResource(location);
            loadPropertySource(location, resource, extension, propertySourceList);
        }
        for (PropertySource<?> propertySource : propertySourceList) {
            propertySources.addLast(propertySource);
        }
    }

    private static void loadPropertySource(String location, Resource resource, String extension,
                                           List<PropertySource<?>> sourceList) {
        if (!resource.exists()) {
            return;
        }
        String name = "basePropertySource: [" + location + "]";
        try {
            Properties properties;
            if ("yml".equals(extension) || "yaml".equals(extension)) {
                YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
                factory.setResources(resource);
                properties = factory.getObject();
                if (properties == null) {
                    properties = new Properties();
                }
            } else if ("properties".equals(extension)) {
                properties = PropertiesLoaderUtils.loadProperties(resource);
            } else {
                throw new IllegalArgumentException("Unsupported property file extension: " + extension);
            }
            sourceList.add(new PropertiesPropertySource(name, properties));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterPropertiesSet() {
        log.info("BasePropertySourcePostProcessor init.");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Getter
    @ToString
    @EqualsAndHashCode
    private static class PropertyFile implements Comparable<PropertyFile> {

        private final int order;
        private final String location;
        private final String extension;
        private final boolean loadActiveProfile;

        PropertyFile(int order, String location, boolean loadActiveProfile) {
            this.order = order;
            this.location = location;
            this.loadActiveProfile = loadActiveProfile;
            this.extension = Objects.requireNonNull(StringUtils.getFilenameExtension(location));
        }

        @Override
        public int compareTo(PropertyFile other) {
            return Integer.compare(this.order, other.order);
        }
    }
}
