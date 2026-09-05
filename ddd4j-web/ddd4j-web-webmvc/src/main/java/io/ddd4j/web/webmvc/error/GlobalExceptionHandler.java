package io.ddd4j.web.webmvc.error;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.exception.BizCheckedException;
import io.ddd4j.core.exception.BizIOException;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.kit.web.IpKit;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import io.ddd4j.web.webmvc.util.WebUtils;
import hitool.core.format.ByteUnitFormat;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extension.context.NestedMessageSource;
import org.springframework.extension.web.multipart.MaxUploadSizePerFileExceededException;
import org.springframework.extension.web.servlet.support.RequestContextUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentConversionNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * 异常增强，以JSON的形式返回给客服端
 * 异常增强类型：NullPointerException,RunTimeException,ClassCastException,
 * NoSuchMethodException,IOException,IndexOutOfBoundsException
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionHandler {

    @Getter
    @Autowired
    private NestedMessageSource messageSource;
    @Autowired
    private ServerI18nProperties serverI18NProperties;

    // --- 4xx Client Error ---

    /**
     * 404 (Not Found)
     */
    @ExceptionHandler({NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiRestResponse<String> noHandlerFoundException(NoHandlerFoundException ex) {
        this.logException(ex);
        String defaultMessage = String.format("没有找到请求地址 [%s],请求方式 [%s]对应的处理对象.", ex.getRequestURL(), ex.getHttpMethod());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "not.found", defaultMessage);
            return ApiCode.SC_NOT_FOUND.toResponse(message);
        }
        return ApiCode.SC_NOT_FOUND.toResponse(defaultMessage);
    }

    /**
     * 405 (Method Not Allowed)
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiRestResponse<String> httpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        this.logException(ex);
        String defaultMessage = String.format("[%s] 不支持的请求方法, 请使用 [%s].", ex.getMethod(), StringUtils.join(ex.getSupportedMethods()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "method.not.allowed", defaultMessage);
            return ApiCode.SC_METHOD_NOT_ALLOWED.toResponse(message);
        }
        return ApiCode.SC_METHOD_NOT_ALLOWED.toResponse(defaultMessage);
    }

    /**
     * 406 (Not Acceptable)
     */
    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class})
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public ApiRestResponse<String> httpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException ex) {
        this.logException(ex);
        String[] supportedMediaTypes = new String[ex.getSupportedMediaTypes().size()];
        for (int i = 0; i < ex.getSupportedMediaTypes().size(); i++) {
            MediaType mediaType = ex.getSupportedMediaTypes().get(i);
            supportedMediaTypes[i] = mediaType.toString();
        }
        String defaultMessage = String.format("不匹配的媒体类型, 仅匹配 [%s].", StringUtils.join(supportedMediaTypes));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.mediaType", defaultMessage);
            return ApiCode.SC_NOT_ACCEPTABLE.toResponse(message);
        }
        return ApiCode.SC_NOT_ACCEPTABLE.toResponse(defaultMessage);
    }

    /**
     * 415 (Unsupported Media Type)
     */
    @ExceptionHandler({HttpMediaTypeNotSupportedException.class})
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiRestResponse<String> httpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        this.logException(ex);
        String defaultMessage = String.format("不支持的媒体类型, 仅支持 [%s].", StringUtils.join(ex.getSupportedMediaTypes()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.mediaType", defaultMessage);
            return ApiCode.SC_UNSUPPORTED_MEDIA_TYPE.toResponse(message);
        }
        return ApiCode.SC_UNSUPPORTED_MEDIA_TYPE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     * <a href="https://www.jianshu.com/p/4df0cac308dc">...</a>
     */
    @ExceptionHandler({MissingMatrixVariableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingMatrixVariableException(MissingMatrixVariableException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少矩阵变量: [%s].", ex.getVariableName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.matrix-variable", defaultMessage);
            return ApiCode.SC_MISSING_MATRIX_VARIABLE.toResponse(message);
        }
        return ApiCode.SC_MISSING_MATRIX_VARIABLE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingPathVariableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingPathVariableException(MissingPathVariableException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少URI模板变量: [%s].", ex.getVariableName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.path-variable", defaultMessage);
            return ApiCode.SC_MISSING_PATH_VARIABLE.toResponse(message);
        }
        return ApiCode.SC_MISSING_PATH_VARIABLE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingRequestCookieException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingRequestCookieException(MissingRequestCookieException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少Cookie变量: [%s].", ex.getCookieName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.cookie", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_COOKIE.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_COOKIE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingRequestHeaderException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingRequestHeaderException(MissingRequestHeaderException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少请求头: [%s].", ex.getHeaderName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.header", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_HEADER.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingServletRequestParameterException(MissingServletRequestParameterException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少参数: [%s]，类型为 [%s].", ex.getParameterName(), ex.getParameterType());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.param", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingServletRequestPartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingServletRequestPartException(MissingServletRequestPartException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少请求对象: [%s].", ex.getRequestPartName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.param", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_PART.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_PART.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({UnsatisfiedServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> unsatisfiedServletRequestParameterException(UnsatisfiedServletRequestParameterException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_UNSATISFIED_PARAM.toResponse(message);
        }
        return ApiCode.SC_UNSATISFIED_PARAM.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ServletRequestBindingException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> servletRequestBindingException(ServletRequestBindingException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_BINDING_ERROR.toResponse(message);
        }
        return ApiCode.SC_BINDING_ERROR.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler(JacksonException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> jsonProcessingException(JacksonException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_PARSING_ERROR.toResponse(message);
        }
        return ApiCode.SC_PARSING_ERROR.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> httpMessageNotReadableException(HttpMessageNotReadableException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_PARSING_ERROR.toResponse(message);
        }
        return ApiCode.SC_PARSING_ERROR.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<List<String>> constraintViolationException(ConstraintViolationException ex) {
        this.logException(ex);

        Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
        Iterator<ConstraintViolation<?>> iterator = constraintViolations.iterator();
        List<String> msgList = new ArrayList<>();
        while (iterator.hasNext()) {
            ConstraintViolation<?> cvl = iterator.next();
            msgList.add(Objects.toString(cvl.getMessage(), cvl.getMessageTemplate()));
        }
        if (CollectionUtils.isEmpty(msgList)) {
            String message = Objects.toString(ex.getMessage(), ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.getReason());
            return ApiRestResponse.of(ApiCode.SC_METHOD_ARGUMENT_NOT_VALID, message, msgList);
        }
        return ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.toResponse(msgList.get(0), msgList);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<?> methodArgumentNotValidException(MethodArgumentNotValidException ex) {
        this.logException(ex);
        return this.bindException(ex, ex.getBindingResult());
    }

    /**
     * 400 (Bad Request)
     *
     * @see javax.validation.Valid
     * @see org.springframework.validation.Validator
     * @see org.springframework.validation.DataBinder
     */
    @ExceptionHandler({BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<?> bindException(BindException ex) {
        this.logException(ex);
        return this.bindException(ex, ex.getBindingResult());
    }


    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<?> webExchangeBindException(WebExchangeBindException ex) {
        this.logException(ex);
        return this.bindException(ex, ex.getBindingResult());
    }

    protected ApiRestResponse<?> bindException(Exception ex, BindingResult result) {
        if (result.getErrorCount() > 0) {
            List<Map<String, String>> errorList = new ArrayList<>();
            for (FieldError error : result.getFieldErrors()) {
                Map<String, String> errorMap = new HashMap<>();
                errorMap.put("field", error.getField());
                errorMap.put("msg", error.getDefaultMessage());
                errorList.add(errorMap);
            }
            String message = result.getFieldErrors()
                    .stream()
                    .findFirst().map(FieldError::getDefaultMessage).orElse(ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.getReason());
            return ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.toResponse(message, errorList);
        } else {
            ObjectError error = result.getGlobalError();
            String message = this.getLocaleMessage(ex, "bad.request", Objects.requireNonNull(error).getDefaultMessage());
            return ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.toResponse(message);
        }
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({InvalidFormatException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> invalidFormatException(InvalidFormatException ex) {
        this.logException(ex);
        String defaultMessage = String.format("JSON 格式错误: %s", ex.getLocation().toString());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({TypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> typeMismatchException(TypeMismatchException ex) {
        this.logException(ex);
        String defaultMessage = String.format("Bean 属性 [%s]类型不匹配. 类型应该是 [%s].", ex.getPropertyName(), ex.getRequiredType());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        this.logException(ex);
        String defaultMessage = String.format("参数类型不匹配，参数[%s]类型应该是 [%s].", ex.getName(), Objects.requireNonNull(ex.getRequiredType()).getSimpleName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MethodArgumentConversionNotSupportedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> methodArgumentConversionNotSupportedException(MethodArgumentConversionNotSupportedException ex) {
        this.logException(ex);
        String defaultMessage = String.format("参数类型转换不支持，参数[%s]类型应该是 [%s].", ex.getName(), Objects.requireNonNull(ex.getRequiredType()).getSimpleName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> validationException(ValidationException ex) {
        this.logException(ex);
        String defaultMessage = "参数校验异常.";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 413 (Payload Too Large)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiRestResponse<String> maxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        this.logException(ex);
        String defaultMessage = String.format("所有文件超过允许的最大限制: %s", ByteUnitFormat.B.to(ByteUnitFormat.K, ex.getMaxUploadSize()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_REQUEST_TOO_LONG.toResponse(message);
        }
        return ApiCode.SC_REQUEST_TOO_LONG.toResponse(defaultMessage);
    }

    /**
     * 413 (Payload Too Large)
     */
    @ExceptionHandler(MaxUploadSizePerFileExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiRestResponse<String> maxUploadSizePerFileExceededException(MaxUploadSizePerFileExceededException ex) {
        this.logException(ex);
        String defaultMessage = String.format("单个文件超过允许的最大限制: %s", ByteUnitFormat.B.to(ByteUnitFormat.K, ex.getMaxUploadSizePerFile()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "bad.request", defaultMessage);
            return ApiCode.SC_REQUEST_TOO_LONG.toResponse(message);
        }
        return ApiCode.SC_REQUEST_TOO_LONG.toResponse(defaultMessage);
    }

    // --- 5xx Server Error ---

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ConstraintDeclarationException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> constraintDeclarationException(ConstraintDeclarationException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：约束声明不合法";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ConstraintDefinitionException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> constraintDefinitionException(ConstraintDefinitionException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：约束定义不合法";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({GroupDefinitionException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> groupDefinitionException(GroupDefinitionException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：约束组定义不合法";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({UnexpectedTypeException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> unexpectedTypeException(UnexpectedTypeException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：参数指定了错误的约束验证器";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({ConversionNotSupportedException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> conversionNotSupportedException(ConversionNotSupportedException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({HttpMessageConversionException.class, HttpMessageNotWritableException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> httpMessageConversionException(HttpMessageConversionException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> nullPointerException(NullPointerException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> classCastException(ClassCastException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> iOException(IOException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.io.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(NoSuchMethodException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> noSuchMethodException(NoSuchMethodException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "no.such.method", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IndexOutOfBoundsException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> indexOutOfBoundsException(IndexOutOfBoundsException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> illegalArgumentException(IllegalArgumentException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /*---------------------业务异常----------------------------*/

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BizRuntimeException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> bizRuntimeException(BizRuntimeException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.runtime.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BizCheckedException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> bizCheckedException(BizCheckedException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.checked.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BizIOException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> bizIOException(BizIOException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.io.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IdempotentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> idempotentException(IdempotentException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.idempotent.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /*---------------------JDBC异常----------------------------*/

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> dataAccessException(DataAccessException ex) {
        this.logException(ex);
        Throwable cause = ex.getCause();
        if (cause instanceof SQLSyntaxErrorException) {
            return sqlSyntaxErrorException((SQLSyntaxErrorException) ex.getCause());
        } else if (cause instanceof SQLIntegrityConstraintViolationException) {
            return sqlIntegrityConstraintViolationException((SQLIntegrityConstraintViolationException) ex.getCause());
        }
        String defaultMessage = "数据库访问异常，请稍后再试";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BatchUpdateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlBatchUpdateException(BatchUpdateException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据批量更新失败，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据保存失败，有重复的数据.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLClientInfoException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlClientInfoException(SQLClientInfoException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库访问异常，客户端配置错误.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLRecoverableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlRecoverableException(SQLRecoverableException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库访问异常，请稍后再试", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlSyntaxErrorException(SQLSyntaxErrorException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：SQL 语法错误，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTimeoutException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTimeoutException(SQLTimeoutException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库连接超时，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTransactionRollbackException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTransactionRollbackException(SQLTransactionRollbackException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库错误，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTransientConnectionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTransientConnectionException(SQLTransientConnectionException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库连接异常，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        log.warn(defaultMessage);
        log.warn("可尝试：1. 增加连接池的大小，2. 检查数据库连接状态，3. 优化SQL查询，4. 调整超时设置");
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTransientException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTransientException(SQLTransientException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据服务器繁忙，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlDuplicateKeyException(DataIntegrityViolationException ex) {
        this.logException(ex);
        String defaultMessage = "数据保存失败，有重复的数据.";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.duplicate.key", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlException(SQLException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库操作失败，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /*---------------------默认全局异常----------------------------*/

    /**
     * 全局异常捕捉处理
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> defaultExceptionHandler(Exception ex) throws Exception {
        this.logException(ex);
        String defaultMessage = ApiCode.SC_INTERNAL_SERVER_ERROR.getReason();
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 异常信息国际化
     */
    protected String getLocaleMessage(Exception ex, String i18nCode, String message) {
        Object[] args = null;
        if (ex instanceof BizCheckedException) {
            BizCheckedException bizEx = (BizCheckedException) ex;
            i18nCode = bizEx.getI18nCode();
            args = bizEx.getArgs();
        } else if (ex instanceof BizIOException) {
            BizIOException bizEx = (BizIOException) ex;
            i18nCode = bizEx.getI18nCode();
            args = bizEx.getArgs();
        } else if (ex instanceof BizRuntimeException) {
            BizRuntimeException bizEx = (BizRuntimeException) ex;
            i18nCode = bizEx.getI18nCode();
            args = bizEx.getArgs();
        }
        if (serverI18NProperties.isEnabled() && StringUtils.isNotBlank(i18nCode)) {
            HttpServletRequest request = WebUtils.getHttpServletRequest();
            Assert.notNull(request, "request cannot be null");
            Locale locale = RequestContextUtils.getLocale(request);
            return getMessageSource().getMessage(i18nCode, args, message, locale);
        }
        return message;
    }

    protected void logException(Exception ex) {
        HttpServletRequest request = WebUtils.getHttpServletRequest();
        if (Objects.nonNull(request)) {
            log.error("URI : {} Request Fail. IP >> {} ", request.getRequestURI(), IpKit.getRemoteAddr(request));
        }
        log.error(ex.getMessage(), ex);
    }

}
