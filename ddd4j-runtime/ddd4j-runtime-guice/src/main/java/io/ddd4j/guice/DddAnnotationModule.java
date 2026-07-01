package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * DDD 注解扫描绑定模块：用 ClassGraph 扫描 DDD 注解标注的类并绑定到 Guice。
 */
@Slf4j
public class DddAnnotationModule extends AbstractModule {

    private static final String[] DDD_ANNOTATION_NAMES = {
            "io.ddd4j.guice.annotation.ddd.DomainService",
            "io.ddd4j.guice.annotation.ddd.DomainRepository",
            "io.ddd4j.guice.annotation.ddd.ApplicationService",
            "io.ddd4j.guice.annotation.ddd.QueryService",
            "io.ddd4j.guice.annotation.ddd.CommandExecutor",
            "io.ddd4j.guice.annotation.ddd.DomainEntity",
            "io.ddd4j.guice.annotation.ddd.DomainValueObject",
            "io.ddd4j.guice.annotation.ddd.DomainGateway",
            "io.ddd4j.guice.annotation.ddd.DomainAssembler",
            "io.ddd4j.guice.annotation.ddd.DomainConverter"
    };

    private final String[] basePackages;
    private final boolean enableClassGraph;

    public DddAnnotationModule(String... basePackages) {
        this(true, basePackages);
    }

    public DddAnnotationModule(boolean enableClassGraph, String... basePackages) {
        this.enableClassGraph = enableClassGraph;
        this.basePackages = Objects.isNull(basePackages) ? new String[0] : basePackages;
    }

    @Override
    protected void configure() {
        if (!enableClassGraph) {
            log.info("DddAnnotationModule scan disabled");
            return;
        }
        scanAndBind();
    }

    private void scanAndBind() {
        long start = System.currentTimeMillis();
        Set<Class<?>> boundClasses = new LinkedHashSet<>();

        try (ScanResult scan = new ClassGraph()
                .acceptPackages(basePackages)
                .enableClassInfo()
                .enableAnnotationInfo()
                .ignoreMethodVisibility()
                .scan()) {
            for (String annotationName : DDD_ANNOTATION_NAMES) {
                bindAnnotatedClasses(scan, annotationName, boundClasses);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("DddAnnotationModule scan completed: packages={}, bound={} beans, elapsed={}ms",
                Arrays.toString(basePackages), boundClasses.size(), elapsed);
    }

    @SuppressWarnings("unchecked")
    private void bindAnnotatedClasses(ScanResult scan, String annotationName, Set<Class<?>> boundClasses) {
        try {
            Class<? extends Annotation> annotationType =
                    (Class<? extends Annotation>) Class.forName(annotationName);
            for (ClassInfo classInfo : scan.getClassesWithAnnotation(annotationName)) {
                bindClass(classInfo, annotationType, boundClasses);
            }
        } catch (ClassNotFoundException exception) {
            log.debug("DDD annotation is not on classpath: {}", annotationName);
        }
    }

    private void bindClass(ClassInfo classInfo, Class<? extends Annotation> annotationType,
                           Set<Class<?>> boundClasses) {
        if (!classInfo.isStandardClass()) {
            return;
        }
        try {
            Class<?> clazz = classInfo.loadClass();
            if (!boundClasses.add(clazz)) {
                return;
            }
            if (isSingletonScoped(clazz, annotationType)) {
                bind(clazz).in(Scopes.SINGLETON);
            } else {
                bind(clazz);
            }
            log.debug("Bound DDD bean: {} (@{})", clazz.getName(), annotationType.getSimpleName());
        } catch (IllegalArgumentException exception) {
            log.warn("Failed to bind DDD bean: {}", classInfo.getName(), exception);
        }
    }

    private boolean isSingletonScoped(Class<?> clazz, Class<? extends Annotation> annotationType) {
        return clazz.isAnnotationPresent(Singleton.class)
                || annotationType.isAnnotationPresent(Singleton.class);
    }
}
