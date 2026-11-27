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
package io.ddd4j.quarkus.extension;

import io.ddd4j.quarkus.annotation.ddd.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * CDI 扩展：扫描 DDD 构造型注解并自动注册为 CDI Bean。
 * <p>
 * 实现 {@link Extension} 接口，在 CDI 容器处理注解类型时识别 DDD 构造型注解
 * （如 {@link DomainService}、{@link ApplicationService} 等），
 * 自动为标注这些注解的类添加 {@link ApplicationScoped} 作用域。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class DddCdiExtension implements Extension {

    /**
     * 检查使用 DDD 组件注解的类型并补充适用的 CDI 元数据。
     *
     * @param <T>   当前容器处理的组件或事件类型
     * @param event 当前框架生命周期或容器启动事件
     */
    <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> event) {
        AnnotatedType<T> type = event.getAnnotatedType();
        String annotationName = resolveAnnotationName(type);
        if (Objects.isNull(annotationName)) {
            return;
        }
        if (!type.isAnnotationPresent(ApplicationScoped.class)) {
            log.debug("Adding @ApplicationScoped to {} {}", annotationName, type.getJavaClass().getName());
            event.configureAnnotatedType().add(new ApplicationScopedLiteral());
        }
    }

    /**
     * 找出待处理类型上可识别的 DDD 注解。
     *
     * @param <T>  当前容器处理的组件或事件类型
     * @param type 要解析的 Bean 类型或 CDI 类型信息
     * @return 已识别的 DDD 注解名，没有时返回空
     */
    private <T> String resolveAnnotationName(AnnotatedType<T> type) {
        if (type.isAnnotationPresent(ApplicationService.class)) {
            return "@ApplicationService";
        }
        if (type.isAnnotationPresent(DomainService.class)) {
            return "@DomainService";
        }
        if (type.isAnnotationPresent(DomainRepository.class)) {
            return "@DomainRepository";
        }
        if (type.isAnnotationPresent(DomainAssembler.class)) {
            return "@DomainAssembler";
        }
        if (type.isAnnotationPresent(DomainConverter.class)) {
            return "@DomainConverter";
        }
        if (type.isAnnotationPresent(DomainEntity.class)) {
            return "@DomainEntity";
        }
        if (type.isAnnotationPresent(DomainValueObject.class)) {
            return "@DomainValueObject";
        }
        if (type.isAnnotationPresent(DomainGateway.class)) {
            return "@DomainGateway";
        }
        if (type.isAnnotationPresent(QueryService.class)) {
            return "@QueryService";
        }
        if (type.isAnnotationPresent(CommandExecutor.class)) {
            return "@CommandExecutor";
        }
        return null;
    }

    /**
     * 为动态注册的 CDI Bean 提供应用级作用域注解实例。
     */
    private static class ApplicationScopedLiteral extends jakarta.enterprise.util.AnnotationLiteral<ApplicationScoped>
            implements ApplicationScoped {

        /**
         * Java 序列化版本标识。
         */
        private static final long serialVersionUID = 1L;
    }
}
