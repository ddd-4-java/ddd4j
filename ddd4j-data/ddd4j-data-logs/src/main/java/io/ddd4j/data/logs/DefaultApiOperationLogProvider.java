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
package io.ddd4j.data.logs;

import io.ddd4j.core.constant.Constants;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认 API 操作日志提供者实现
 * <p>通过 AOP 切面记录 API 调用日志，包括请求 URI、IP、参数、耗时等信息</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class DefaultApiOperationLogProvider implements ApiOperationLogProvider {

    /**
     * 访问日志 Marker（由 core 常量构造，保证 Marker 过滤语义，见 ADR-0002）。
     */
    private static final Marker ACCESS_MARKER = MarkerFactory.getMarker(Constants.ACCESS_MARKER);

    /**
     * \u5728\u88ab\u62e6\u622a\u64cd\u4f5c\u6267\u884c\u524d\u4fdd\u7559\u6269\u5c55\u94a9\u5b50\u3002
     * @param joinPoint \u5f53\u524d API \u8c03\u7528\u8fde\u63a5\u70b9
     * @param apiOperation \u88ab\u62e6\u622a\u65b9\u6cd5\u58f0\u660e\u7684\u64cd\u4f5c\u8bf4\u660e
     */
    @Override
    public void doBefore(JoinPoint joinPoint, Operation apiOperation) {

    }

    /**
     * \u7528\u6267\u884c\u7ed3\u679c\u548c\u8ba1\u65f6\u8bb0\u5f55\u6210\u529f\u7ed3\u675f\u7684 API \u8c03\u7528\u3002
     * @param joinPoint \u5f53\u524d API \u8c03\u7528\u8fde\u63a5\u70b9
     * @param apiOperation \u88ab\u62e6\u622a\u65b9\u6cd5\u58f0\u660e\u7684\u64cd\u4f5c\u8bf4\u660e
     * @param rt \u4e1a\u52a1\u65b9\u6cd5\u8fd4\u56de\u503c
     * @param stopWatch \u8bb0\u5f55\u672c\u6b21\u64cd\u4f5c\u8017\u65f6\u7684\u8ba1\u65f6\u5668
     */
    @Override
    public void afterReturing(JoinPoint joinPoint, Operation apiOperation, Object rt, com.google.common.base.Stopwatch stopWatch) {
        this.doApiOperationLog(joinPoint, apiOperation, rt, null, stopWatch);
    }

    /**
     * \u7528\u539f\u59cb\u5f02\u5e38\u548c\u8ba1\u65f6\u8bb0\u5f55\u5f02\u5e38\u7ed3\u675f\u7684 API \u8c03\u7528\u3002
     * @param joinPoint \u5f53\u524d API \u8c03\u7528\u8fde\u63a5\u70b9
     * @param apiOperation \u88ab\u62e6\u622a\u65b9\u6cd5\u58f0\u660e\u7684\u64cd\u4f5c\u8bf4\u660e
     * @param ex \u4e1a\u52a1\u65b9\u6cd5\u629b\u51fa\u7684\u5f02\u5e38
     * @param stopWatch \u8bb0\u5f55\u672c\u6b21\u64cd\u4f5c\u8017\u65f6\u7684\u8ba1\u65f6\u5668
     */
    @Override
    public void afterThrowing(JoinPoint joinPoint, Operation apiOperation, Throwable ex, com.google.common.base.Stopwatch stopWatch) {
        this.doApiOperationLog(joinPoint, apiOperation, null, ex, stopWatch);
    }

    /**
     * 执行操作日志记录
     *
     * @param joinPoint    连接点
     * @param apiOperation API 操作注解
     * @param rt           返回值
     * @param ex           异常信息
     * @param stopWatch    性能计时器
     */
    protected void doApiOperationLog(JoinPoint joinPoint, Operation apiOperation, Object rt, Throwable ex, com.google.common.base.Stopwatch stopWatch) {

        // 1、获取AOP信息
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;

        // 2、获取方法及参数信息
        Method method = methodSignature.getMethod();
        String methodName = methodSignature.getName();

        // 3、获取 Hidden 注解，如果获取到了，则不进行日志记录
        Hidden hidden = method.getAnnotation(Hidden.class);
        if (Objects.isNull(hidden)) {
            hidden = method.getDeclaringClass().getDeclaredAnnotation(Hidden.class);
        }

        // 4、判断是否需要记录日志
        boolean needLog = log.isInfoEnabled() && Objects.isNull(hidden);
        if (!needLog) {
            log.info(ACCESS_MARKER, "Stopwatch: {}", stopWatch);
            return;
        }

        // 5、获取 Request对象，解析请求来源
        ApiOperationLogRequestContext requestContext = ApiOperationLogRequestContext.current();
        String uri = requestContext.requestUri();
        String ipAddress = requestContext.clientIp();
        if (Objects.nonNull(uri) || Objects.nonNull(ipAddress)) {
            log.info(ACCESS_MARKER, " >> URI {} IP {} ", uri, ipAddress);
        }

        // 6、筛选出有意义的参数
        Object[] args = joinPoint.getArgs();
        List<Object> methodArgs = Objects.isNull(args) ? null : Stream.of(args)
                .filter(requestContext::includeArgument)
                .collect(Collectors.toList());

        // 5、如果开启日志，则发送日志消息
        this.saveLog(joinPoint, method, apiOperation, rt, ex, stopWatch);

        if (Objects.isNull(ex)) {
            log.info(ACCESS_MARKER, " >> invoke method {} with args {} Success! elapsed={}", methodName, methodArgs, stopWatch);
        } else {
            log.error(ACCESS_MARKER, " >> invoke method {} with args {} error {} elapsed={}", methodName, methodArgs, ex.getMessage(), stopWatch);
        }

        log.info(ACCESS_MARKER, "Stopwatch: {}", stopWatch);
    }

    /**
     * 保存日志（子类可重写此方法实现持久化）
     *
     * @param joinPoint    连接点
     * @param method       方法
     * @param apiOperation API 操作注解
     * @param rt           返回值
     * @param ex           异常信息
     * @param stopWatch    性能计时器
     */
    protected void saveLog(JoinPoint joinPoint, Method method, Operation apiOperation, Object rt, Throwable ex, com.google.common.base.Stopwatch stopWatch) {
        // do nothing
    }

}
