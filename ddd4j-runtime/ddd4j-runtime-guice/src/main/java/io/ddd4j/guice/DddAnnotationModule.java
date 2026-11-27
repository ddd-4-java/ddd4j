/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class DddAnnotationModule extends AbstractModule {

    /** \u626b\u63cf\u65f6\u8bc6\u522b\u5e76\u6ce8\u518c\u4e3a Guice \u7ec4\u4ef6\u7684 DDD \u6ce8\u89e3\u540d\u3002 */
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

    /**
     * 待扫描的基础包路径
     */
    private final String[] basePackages;
    /**
     * 是否启用 ClassGraph 自动扫描
     */
    private final boolean enableClassGraph;

    /**
     * 创建 DDD 注解绑定模块（默认启用 ClassGraph 扫描）。
     *
     * @param basePackages 待扫描的基础包路径
     */
    public DddAnnotationModule(String... basePackages) {
        this(true, basePackages);
    }

    /**
     * 创建 DDD 注解绑定模块。
     *
     * @param enableClassGraph 是否启用 ClassGraph 自动扫描
     * @param basePackages     待扫描的基础包路径
     */
    public DddAnnotationModule(boolean enableClassGraph, String... basePackages) {
        this.enableClassGraph = enableClassGraph;
        this.basePackages = Objects.isNull(basePackages) ? new String[0] : basePackages;
    }

    /** \u5728 Guice \u88c5\u914d\u65f6\u626b\u63cf\u5e76\u7ed1\u5b9a\u53ef\u8bc6\u522b\u7684 DDD \u6ce8\u89e3\u7ec4\u4ef6\u3002 */
    @Override
    protected void configure() {
        if (!enableClassGraph) {
            log.info("DddAnnotationModule scan disabled");
            return;
        }
        scanAndBind();
    }

    /** \u626b\u63cf\u7ea6\u5b9a\u5305\u5e76\u6309 DDD \u6ce8\u89e3\u7ed1\u5b9a\u5c1a\u672a\u767b\u8bb0\u7684\u7c7b\u3002 */
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

    /**
     * \u6536\u96c6\u5177\u6709\u6307\u5b9a DDD \u6ce8\u89e3\u7684\u5019\u9009\u5e76\u4e3a\u5176\u5efa\u7acb Guice \u7ed1\u5b9a\u3002
     * @param scan \u5f53\u524d\u6ce8\u89e3\u626b\u63cf\u7ed3\u679c
     * @param annotationName \u67e5\u627e\u7ec4\u4ef6\u65f6\u4f7f\u7528\u7684\u5168\u9650\u5b9a\u6ce8\u89e3\u540d
     * @param boundClasses \u5df2\u5efa\u7acb\u7ed1\u5b9a\u7684\u7ec4\u4ef6\u7c7b\u578b
     */
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

    /**
     * \u6309\u7ec4\u4ef6\u7c7b\u578b\u4e0e\u4f5c\u7528\u57df\u7ed1\u5b9a\u5355\u4e2a\u6ce8\u89e3\u626b\u63cf\u7ed3\u679c\u3002
     * @param classInfo \u5f85\u8f6c\u6210 Guice \u670d\u52a1\u7ed1\u5b9a\u7684\u626b\u63cf\u7c7b\u8bb0\u5f55
     * @param annotationType \u5f53\u524d\u8981\u8bc6\u522b\u7684\u6ce8\u89e3\u7c7b\u578b
     * @param boundClasses \u5df2\u5efa\u7acb\u7ed1\u5b9a\u7684\u7ec4\u4ef6\u7c7b\u578b
     */
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

    /**
     * \u6839\u636e\u7c7b\u548c\u6846\u67b6\u6ce8\u89e3\u5224\u65ad\u7ec4\u4ef6\u5e94\u5426\u4f7f\u7528\u5355\u4f8b\u7ed1\u5b9a\u3002
     * @param clazz \u5f85\u9a8c\u8bc1\u751f\u547d\u5468\u671f\u6216\u4f5c\u7528\u57df\u7684\u7ec4\u4ef6\u7c7b\u578b
     * @param annotationType \u5f53\u524d\u8981\u8bc6\u522b\u7684\u6ce8\u89e3\u7c7b\u578b
     * @return \u9700\u8981\u5355\u4f8b\u7ed1\u5b9a\u65f6\u8fd4\u56de true
     */
    private boolean isSingletonScoped(Class<?> clazz, Class<? extends Annotation> annotationType) {
        return clazz.isAnnotationPresent(Singleton.class)
                || annotationType.isAnnotationPresent(Singleton.class);
    }
}
