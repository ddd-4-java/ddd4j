package io.ddd4j.web.webmvc.core;

import io.ddd4j.core.api.R;
import io.ddd4j.core.api.ResultCode;
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

@RestControllerAdvice
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-WEB : GlobalRestExceptionAdvice ###")
public class GlobalRestExceptionAdvice {

    @ExceptionHandler({BindException.class})
    public R<String> bindException(HttpServletRequest request, Model model, BindException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        List<String> errList = e.getFieldErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage).collect(Collectors.toList());
        log.error("请求参数校验失败：{} {}\n**StackTraces:** {}", errList, model, projectStackTrace);
        return R.fail(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), String.join(",", errList));
    }

    @ExceptionHandler({ValidateException.class})
    public R<String> validatorException(HttpServletRequest request, ValidateException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("请求参数校验失败：{}\n**StackTraces:** {}", e.getMessage(), projectStackTrace);
        return R.fail(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public R<String> methodArgumentNotValidExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("请求参数校验失败：{}\n**StackTraces:** {}", fieldError.getDefaultMessage(), projectStackTrace);
        return R.fail(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), fieldError.getDefaultMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<String> handle(HttpServletRequest request, NoHandlerFoundException e) {
        log.error("", e);
        return R.fail(404, "地址错误！！！" + request.getRequestURI() + "非法访问!");
    }

    @ExceptionHandler({BizRuntimeException.class})
    public R<String> serviceException(HttpServletRequest request, BizRuntimeException e) {
        log.warn("服务异常：", e);
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({NullPointerException.class})
    public R<String> nullPointerException(HttpServletRequest request, NullPointerException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("空指针异常\n**StackTraces:** {}", projectStackTrace);
        return R.fail(ResultCode.FAIL.getCode(), e.getMessage());
    }

    @ExceptionHandler({RuntimeException.class})
    public R<String> runTimeException(HttpServletRequest request, RuntimeException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("运行时异常\n**StackTraces:** {}", projectStackTrace);
        return R.fail(ResultCode.FAIL.getCode(), e.getMessage());
    }

}
