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

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.annotation.api.ApiIdempotent;
import io.swagger.v3.oas.annotations.Hidden;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
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
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Stream;

/**
 * 幂等性工具类。
 * <p>提供基于 {@link ApiIdempotent} 注解的幂等键生成能力，支持 SpEL 表达式和自动路由拼接。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class IdempotentKit {

    /**
     * SpEL 表达式解析器
     */
    protected static final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 生成幂等性键。
     * <p>根据 {@link ApiIdempotent} 注解配置生成幂等键：
     * <ul>
     *   <li>如果注解指定了 value 且为非 SpEL 模式，直接返回该值</li>
     *   <li>如果为 SpEL 模式，解析表达式后返回值</li>
     *   <li>如果未指定 value，则拼接路由和方法参数后计算 MD5</li>
     * </ul>
     *
     * @param joinPoint  切点
     * @param idempotent 幂等注解
     * @return 幂等键
     * @throws IOException 参数解析异常
     */
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
