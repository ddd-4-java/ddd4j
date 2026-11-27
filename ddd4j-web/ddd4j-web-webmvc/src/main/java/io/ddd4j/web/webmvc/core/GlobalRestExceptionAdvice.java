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
package io.ddd4j.web.webmvc.core;

import io.ddd4j.core.api.R;
import io.ddd4j.core.api.ApiCode;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.ValidateException;
import io.ddd4j.core.util.ExceptionKit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局 REST 异常通知处理器。
 * <p>统一处理参数校验异常、业务运行时异常、NPE、404 及通用运行时异常，返回统一错误响应。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestControllerAdvice
@Slf4j(topic = "### BASE-WEB : GlobalRestExceptionAdvice ###")
public class GlobalRestExceptionAdvice {

    /**
     * \u5904\u7406\u53c2\u6570\u7ed1\u5b9a\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param model \u89c6\u56fe\u6a21\u578b
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler({BindException.class})
    public R<String> bindException(HttpServletRequest request, Model model, BindException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        List<String> errList = e.getFieldErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.toList());
        log.error("请求参数校验失败：{} {}\n**StackTraces:** {}", errList, model, projectStackTrace);
        return R.of(ApiCode.BAD_REQUEST.getCode(), String.join(",", errList));
    }

    /**
     * \u5904\u7406\u53c2\u6570\u6821\u9a8c\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler({ValidateException.class})
    public R<String> validatorException(HttpServletRequest request, ValidateException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("请求参数校验失败：{}\n**StackTraces:** {}", e.getMessage(), projectStackTrace);
        return R.of(ApiCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * \u5904\u7406\u65b9\u6cd5\u53c2\u6570\u65e0\u6548\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public R<String> methodArgumentNotValidExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("请求参数校验失败：{}\n**StackTraces:** {}", fieldError.getDefaultMessage(), projectStackTrace);
        return R.of(ApiCode.BAD_REQUEST.getCode(), fieldError.getDefaultMessage());
    }

    /**
     * \u5904\u7406 404 \u5730\u5740\u672a\u627e\u5230\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<String> handle(HttpServletRequest request, NoHandlerFoundException e) {
        log.error("", e);
        return R.of(404, "地址错误！！！" + request.getRequestURI() + "非法访问!");
    }

    /**
     * \u5904\u7406\u4e1a\u52a1\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler({BizRuntimeException.class})
    public R<String> serviceException(HttpServletRequest request, BizRuntimeException e) {
        log.warn("服务异常：", e);
        return R.fail(e.getMessage());
    }

    /**
     * \u5904\u7406\u7a7a\u6307\u9488\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler({NullPointerException.class})
    public R<String> nullPointerException(HttpServletRequest request, NullPointerException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("空指针异常\n**StackTraces:** {}", projectStackTrace);
        return R.of(ApiCode.FAIL.getCode(), e.getMessage());
    }

    /**
     * \u5904\u7406\u901a\u7528\u8fd0\u884c\u65f6\u5f02\u5e38\u3002
     *
     * @return \u6807\u51c6\u9519\u8bef\u54cd\u5e94
     * @param request HTTP \u8bf7\u6c42
     * @param e \u6355\u83b7\u7684\u5f02\u5e38
     */
    @ExceptionHandler({RuntimeException.class})
    public R<String> runTimeException(HttpServletRequest request, RuntimeException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("运行时异常\n**StackTraces:** {}", projectStackTrace);
        return R.of(ApiCode.FAIL.getCode(), e.getMessage());
    }

}
