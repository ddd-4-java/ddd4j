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
