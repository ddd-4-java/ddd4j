package io.ddd4j.web.webmvc.core;

import io.ddd4j.core.contract.R;
import io.ddd4j.core.contract.enums.ResultCode;
import io.ddd4j.core.contract.exception.ServiceException;
import io.ddd4j.core.contract.exception.ValidateException;
import io.ddd4j.core.util.ExceptionKit;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class GlobalRestExceptionAdvice {
    private static final Logger log = LoggerFactory.getLogger("### BASE-WEB : GlobalRestExceptionAdvice ###");

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
        e.printStackTrace();
        return R.fail(ResultCode.PARAMETER_VALIDATION_FAILED.getCode(), fieldError.getDefaultMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<String> handle(HttpServletRequest request, NoHandlerFoundException e) {
        log.error("", e);
        return R.fail(404, "地址错误！！！" + request.getRequestURI() + "非法访问!");
    }

    @ExceptionHandler({ServiceException.class})
    public R<String> serviceException(HttpServletRequest request, ServiceException e) {
        log.warn("服务异常：", e);
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({NullPointerException.class})
    public R<String> nullPointerException(HttpServletRequest request, NullPointerException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("空指针异常\n**StackTraces:** {}", projectStackTrace);
        e.printStackTrace();
        return R.fail(ResultCode.FAIL.getCode(), e.getMessage());
    }

    @ExceptionHandler({RuntimeException.class})
    public R<String> runTimeException(HttpServletRequest request, RuntimeException e) {
        String projectStackTrace = ExceptionKit.getProjectStackTraces(e);
        log.error("运行时异常\n**StackTraces:** {}", projectStackTrace);
        e.printStackTrace();
        return R.fail(ResultCode.FAIL.getCode(), e.getMessage());
    }

}
