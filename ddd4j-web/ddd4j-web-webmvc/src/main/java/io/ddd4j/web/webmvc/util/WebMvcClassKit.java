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
package io.ddd4j.web.webmvc.util;

import lombok.experimental.UtilityClass;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.util.Objects;

/**
 * Spring WebMVC 处理器方法工具。
 */
@UtilityClass
public class WebMvcClassKit {

    public <A extends Annotation> A getAnnotation(HandlerMethod handlerMethod, Class<A> annotationType) {
        Objects.requireNonNull(handlerMethod, "handlerMethod must not be null");
        Objects.requireNonNull(annotationType, "annotationType must not be null");
        A annotation = handlerMethod.getMethodAnnotation(annotationType);
        if (Objects.nonNull(annotation)) {
            return annotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), annotationType);
    }
}
