package io.ddd4j.spring.util;

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.annotation.api.ApiIdempotent;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

@Slf4j
public class IdempotentKit {

    protected static final ExpressionParser expressionParser = new SpelExpressionParser();

    public static String getIdempotentKey(ProceedingJoinPoint joinPoint, ApiIdempotent idempotent) throws IOException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        String[] parameterNames = methodSignature.getParameterNames();
        Object[] parameters = joinPoint.getArgs();
        if (StringUtils.hasText(idempotent.value())) {
            if (idempotent.spel()) {
                EvaluationContext context = new StandardEvaluationContext();
                for (int i = 0; i < parameterNames.length; i++) {
                    if (parameters[i] instanceof ServletRequest || parameters[i] instanceof ServletResponse) {
                        continue;
                    }
                    context.setVariable(parameterNames[i], parameters[i]);
                }
                return String.valueOf(expressionParser.parseExpression(idempotent.value()).getValue(context));
            }
            return idempotent.value();
        } else {
            StringJoiner joiner = new StringJoiner("");
            RequestMapping requestMapping = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequestMapping.class);
            if (Objects.nonNull(requestMapping)) {
                log.debug("requestMapping: {}", JSONObject.toJSONString(requestMapping));
                Stream.of(Objects.isNull(requestMapping.value()) || ArrayUtils.isEmpty(requestMapping.value())
                        ? requestMapping.path()
                        : requestMapping.value()).findFirst().ifPresent(joiner::add);
            }
            PostMapping postMapping = AnnotationUtils.findAnnotation(method, PostMapping.class);
            if (Objects.nonNull(postMapping)) {
                log.debug("postMapping: {}", JSONObject.toJSONString(postMapping));
                Stream.of(Objects.isNull(postMapping.value()) || ArrayUtils.isEmpty(postMapping.value())
                        ? postMapping.path()
                        : postMapping.value()).findFirst().ifPresent(joiner::add);
            }
            GetMapping getMapping = AnnotationUtils.findAnnotation(method, GetMapping.class);
            if (Objects.nonNull(getMapping)) {
                Stream.of(Objects.isNull(getMapping.value()) || ArrayUtils.isEmpty(getMapping.value())
                        ? getMapping.path()
                        : getMapping.value()).findFirst().ifPresent(joiner::add);
                log.debug("getMapping: {}", JSONObject.toJSONString(getMapping));
                joiner.add(getMapping.path()[0]);
            }
            RequestMapping methodRequestMapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
            if (Objects.nonNull(methodRequestMapping)) {
                log.debug("requestMapping: {}", JSONObject.toJSONString(methodRequestMapping));
                Stream.of(Objects.isNull(methodRequestMapping.value()) || ArrayUtils.isEmpty(methodRequestMapping.value())
                        ? methodRequestMapping.path()
                        : methodRequestMapping.value()).findFirst().ifPresent(joiner::add);
            }
            DeleteMapping deleteMapping = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
            if (Objects.nonNull(deleteMapping)) {
                log.debug("deleteMapping: {}", JSONObject.toJSONString(deleteMapping));
                Stream.of(Objects.isNull(deleteMapping.value()) || ArrayUtils.isEmpty(deleteMapping.value())
                        ? deleteMapping.path()
                        : deleteMapping.value()).findFirst().ifPresent(joiner::add);
            }
            PatchMapping patchMapping = AnnotationUtils.findAnnotation(method, PatchMapping.class);
            if (Objects.nonNull(patchMapping)) {
                log.debug("patchMapping: {}", JSONObject.toJSONString(patchMapping));
                Stream.of(Objects.isNull(patchMapping.value()) || ArrayUtils.isEmpty(patchMapping.value())
                        ? patchMapping.path()
                        : patchMapping.value()).findFirst().ifPresent(joiner::add);
            }
            PutMapping putMapping = AnnotationUtils.findAnnotation(method, PutMapping.class);
            if (Objects.nonNull(putMapping)) {
                log.debug("putMapping: {}", JSONObject.toJSONString(putMapping));
                Stream.of(Objects.isNull(putMapping.value()) || ArrayUtils.isEmpty(putMapping.value())
                        ? putMapping.path()
                        : putMapping.value()).findFirst().ifPresent(joiner::add);
            }
            if (idempotent.withArgs()) {
                Annotation[][] paramAnnotations = method.getParameterAnnotations();
                for (int i = 0; i < joinPoint.getArgs().length; i++) {
                    if (Stream.of(paramAnnotations[i]).anyMatch(annt -> annt instanceof Hidden)) {
                        continue;
                    }
                    if (Objects.isNull(joinPoint.getArgs()[i]) || joinPoint.getArgs()[i] instanceof ServletRequest
                            || joinPoint.getArgs()[i] instanceof ServletResponse) {
                        continue;
                    }
                    joiner.add(JSONObject.toJSONString(joinPoint.getArgs()[i]));
                }
            }
            return DigestUtils.md5DigestAsHex(joiner.toString().getBytes());
        }
    }

}
