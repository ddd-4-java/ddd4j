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

    /**
     * 扫描时识别并注册为 Guice 组件的 DDD 注解名。
     */
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

    /**
     * 在 Guice 装配时扫描并绑定可识别的 DDD 注解组件。
     */
    @Override
    protected void configure() {
        if (!enableClassGraph) {
            log.info("DddAnnotationModule scan disabled");
            return;
        }
        scanAndBind();
    }

    /**
     * 扫描约定包并按 DDD 注解绑定尚未登记的类。
     */
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
     * 收集具有指定 DDD 注解的候选并为其建立 Guice 绑定。
     *
     * @param scan           当前注解扫描结果
     * @param annotationName 查找组件时使用的全限定注解名
     * @param boundClasses   已建立绑定的组件类型
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
     * 按组件类型与作用域绑定单个注解扫描结果。
     *
     * @param classInfo      待转成 Guice 服务绑定的扫描类记录
     * @param annotationType 当前要识别的注解类型
     * @param boundClasses   已建立绑定的组件类型
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
     * 根据类和框架注解判断组件应否使用单例绑定。
     *
     * @param clazz          待验证生命周期或作用域的组件类型
     * @param annotationType 当前要识别的注解类型
     * @return 需要单例绑定时返回 true
     */
    private boolean isSingletonScoped(Class<?> clazz, Class<? extends Annotation> annotationType) {
        return clazz.isAnnotationPresent(Singleton.class)
                || annotationType.isAnnotationPresent(Singleton.class);
    }
}
