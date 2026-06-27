package io.ddd4j.web.webflux.error;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hitool.core.format.ByteUnitFormat;
import io.ddd4j.web.webflux.config.ServerI18nProperties;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.exception.BizCheckedException;
import io.ddd4j.core.exception.BizIOException;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.IdempotentException;
import jakarta.validation.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.biz.context.NestedMessageSource;
import org.springframework.biz.web.multipart.MaxUploadSizePerFileExceededException;
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
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * 异常增强，以JSON的形式返回给客服端
 * 异常增强类型：NullPointerException,RunTimeException,ClassCastException,
 * NoSuchMethodException,IOException,IndexOutOfBoundsException
 */
@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionHandler extends BaseExceptionHandler {

    @Getter
    @Autowired
    private NestedMessageSource messageSource;
    @Autowired
    private ServerI18nProperties serverI18NProperties;

    // --- 4xx Client Error ---

    /**
     * 405 (Method Not Allowed)
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class})
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiRestResponse<String> httpRequestMethodNotSupportedException(ServerWebExchange exchange, HttpRequestMethodNotSupportedException ex) {
        this.logException(ex);
        String defaultMessage = String.format("[%s] 不支持的请求方法, 请使用 [%s].", ex.getMethod(), StringUtils.join(ex.getSupportedMethods()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "method.not.allowed", defaultMessage);
            return ApiCode.SC_METHOD_NOT_ALLOWED.toResponse(message);
        }
        return ApiCode.SC_METHOD_NOT_ALLOWED.toResponse(defaultMessage);
    }

    /**
     * 406 (Not Acceptable)
     */
    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class})
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    public ApiRestResponse<String> httpMediaTypeNotAcceptableException(ServerWebExchange exchange, HttpMediaTypeNotAcceptableException ex) {
        this.logException(ex);
        String[] supportedMediaTypes = new String[ex.getSupportedMediaTypes().size()];
        for (int i = 0; i < ex.getSupportedMediaTypes().size(); i++) {
            MediaType mediaType = ex.getSupportedMediaTypes().get(i);
            supportedMediaTypes[i] = mediaType.toString();
        }
        String defaultMessage = String.format("不匹配的媒体类型, 仅匹配 [%s].", StringUtils.join(supportedMediaTypes));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.mediaType", defaultMessage);
            return ApiCode.SC_NOT_ACCEPTABLE.toResponse(message);
        }
        return ApiCode.SC_NOT_ACCEPTABLE.toResponse(defaultMessage);
    }

    /**
     * 415 (Unsupported Media Type)
     */
    @ExceptionHandler({HttpMediaTypeNotSupportedException.class})
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiRestResponse<String> httpMediaTypeNotSupportedException(ServerWebExchange exchange, HttpMediaTypeNotSupportedException ex) {
        this.logException(ex);
        String defaultMessage = String.format("不支持的媒体类型, 仅支持 [%s].", StringUtils.join(ex.getSupportedMediaTypes()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.mediaType", defaultMessage);
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
    public ApiRestResponse<String> missingMatrixVariableException(ServerWebExchange exchange, MissingMatrixVariableException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少矩阵变量: [%s].", ex.getVariableName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.matrix-variable", defaultMessage);
            return ApiCode.SC_MISSING_MATRIX_VARIABLE.toResponse(message);
        }
        return ApiCode.SC_MISSING_MATRIX_VARIABLE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingPathVariableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingPathVariableException(ServerWebExchange exchange, MissingPathVariableException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少URI模板变量: [%s].", ex.getVariableName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.path-variable", defaultMessage);
            return ApiCode.SC_MISSING_PATH_VARIABLE.toResponse(message);
        }
        return ApiCode.SC_MISSING_PATH_VARIABLE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingRequestCookieException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingRequestCookieException(ServerWebExchange exchange, MissingRequestCookieException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少Cookie变量: [%s].", ex.getCookieName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.cookie", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_COOKIE.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_COOKIE.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingRequestHeaderException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingRequestHeaderException(ServerWebExchange exchange, MissingRequestHeaderException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少请求头: [%s].", ex.getHeaderName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.header", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingServletRequestParameterException(ServerWebExchange exchange, MissingServletRequestParameterException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少参数: [%s]，类型为 [%s].", ex.getParameterName(), ex.getParameterType());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.param", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_PARAM.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MissingServletRequestPartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> missingServletRequestPartException(ServerWebExchange exchange, MissingServletRequestPartException ex) {
        this.logException(ex);
        String defaultMessage = String.format("缺少请求对象: [%s].", ex.getRequestPartName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.param", defaultMessage);
            return ApiCode.SC_MISSING_REQUEST_PART.toResponse(message);
        }
        return ApiCode.SC_MISSING_REQUEST_PART.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({UnsatisfiedServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> unsatisfiedServletRequestParameterException(ServerWebExchange exchange, UnsatisfiedServletRequestParameterException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_UNSATISFIED_PARAM.toResponse(message);
        }
        return ApiCode.SC_UNSATISFIED_PARAM.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ServletRequestBindingException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> servletRequestBindingException(ServerWebExchange exchange, ServletRequestBindingException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_BINDING_ERROR.toResponse(message);
        }
        return ApiCode.SC_BINDING_ERROR.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({JsonParseException.class, JsonProcessingException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> jsonProcessingException(ServerWebExchange exchange, JsonProcessingException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_PARSING_ERROR.toResponse(message);
        }
        return ApiCode.SC_PARSING_ERROR.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> httpMessageNotReadableException(ServerWebExchange exchange, HttpMessageNotReadableException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request.param", ex.getMessage());
            return ApiCode.SC_PARSING_ERROR.toResponse(message);
        }
        return ApiCode.SC_PARSING_ERROR.toResponse();
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<List<String>> constraintViolationException(ServerWebExchange exchange, ConstraintViolationException ex) {
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
    public ApiRestResponse<?> methodArgumentNotValidException(ServerWebExchange exchange, MethodArgumentNotValidException ex) {
        this.logException(ex);
        return this.bindException(exchange, ex, ex.getBindingResult());
    }

    /**
     * 400 (Bad Request)
     *
     * @see jakarta.validation.Valid
     * @see org.springframework.validation.Validator
     * @see org.springframework.validation.DataBinder
     */
    @ExceptionHandler({BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<?> bindException(ServerWebExchange exchange, BindException ex) {
        this.logException(ex);
        return this.bindException(exchange, ex, ex.getBindingResult());
    }


    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<?> webExchangeBindException(ServerWebExchange exchange, WebExchangeBindException ex) {
        this.logException(ex);
        return this.bindException(exchange, ex, ex.getBindingResult());
    }

    protected ApiRestResponse<?> bindException(ServerWebExchange exchange, Exception ex, BindingResult result) {
        if (result.getErrorCount() > 0) {
            List<Map<String, String>> errorList = Lists.newArrayList();
            for (FieldError error : result.getFieldErrors()) {
                Map<String, String> errorMap = Maps.newHashMap();
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
            String message = this.getLocaleMessage(exchange, ex, "bad.request", Objects.requireNonNull(error).getDefaultMessage());
            return ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.toResponse(message);
        }
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({InvalidFormatException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> invalidFormatException(ServerWebExchange exchange, InvalidFormatException ex) {
        this.logException(ex);
        String defaultMessage = String.format("JSON 格式错误: %s", ex.getLocation().toString());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
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
    public ApiRestResponse<String> typeMismatchException(ServerWebExchange exchange, TypeMismatchException ex) {
        this.logException(ex);
        String defaultMessage = String.format("Bean 属性 [%s]类型不匹配. 类型应该是 [%s].", ex.getPropertyName(), ex.getRequiredType());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> methodArgumentTypeMismatchException(ServerWebExchange exchange, MethodArgumentTypeMismatchException ex) {
        this.logException(ex);
        String defaultMessage = String.format("参数类型不匹配，参数[%s]类型应该是 [%s].", ex.getName(), Objects.requireNonNull(ex.getRequiredType()).getSimpleName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({MethodArgumentConversionNotSupportedException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> methodArgumentConversionNotSupportedException(ServerWebExchange exchange, MethodArgumentConversionNotSupportedException ex) {
        this.logException(ex);
        String defaultMessage = String.format("参数类型转换不支持，参数[%s]类型应该是 [%s].", ex.getName(), Objects.requireNonNull(ex.getRequiredType()).getSimpleName());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiRestResponse<String> validationException(ServerWebExchange exchange, ValidationException ex) {
        this.logException(ex);
        String defaultMessage = "参数校验异常.";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
            return ApiCode.SC_BAD_REQUEST.toResponse(message);
        }
        return ApiCode.SC_BAD_REQUEST.toResponse(defaultMessage);
    }

    /**
     * 413 (Payload Too Large)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiRestResponse<String> maxUploadSizeExceededException(ServerWebExchange exchange, MaxUploadSizeExceededException ex) {
        this.logException(ex);
        String defaultMessage = String.format("所有文件超过允许的最大限制: %s", ByteUnitFormat.B.to(ByteUnitFormat.K, ex.getMaxUploadSize()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
            return ApiCode.SC_REQUEST_TOO_LONG.toResponse(message);
        }
        return ApiCode.SC_REQUEST_TOO_LONG.toResponse(defaultMessage);
    }

    /**
     * 413 (Payload Too Large)
     */
    @ExceptionHandler(MaxUploadSizePerFileExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiRestResponse<String> maxUploadSizePerFileExceededException(ServerWebExchange exchange, MaxUploadSizePerFileExceededException ex) {
        this.logException(ex);
        String defaultMessage = String.format("单个文件超过允许的最大限制: %s", ByteUnitFormat.B.to(ByteUnitFormat.K, ex.getMaxUploadSizePerFile()));
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "bad.request", defaultMessage);
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
    public ApiRestResponse<String> constraintDeclarationException(ServerWebExchange exchange, ConstraintDeclarationException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：约束声明不合法";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 400 (Bad Request)
     */
    @ExceptionHandler({ConstraintDefinitionException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> constraintDefinitionException(ServerWebExchange exchange, ConstraintDefinitionException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：约束定义不合法";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({GroupDefinitionException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> groupDefinitionException(ServerWebExchange exchange, GroupDefinitionException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：约束组定义不合法";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({UnexpectedTypeException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> unexpectedTypeException(ServerWebExchange exchange, UnexpectedTypeException ex) {
        this.logException(ex);
        String defaultMessage = "参数约束异常：参数指定了错误的约束验证器";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({ConversionNotSupportedException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> conversionNotSupportedException(ServerWebExchange exchange, ConversionNotSupportedException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler({HttpMessageConversionException.class, HttpMessageNotWritableException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> httpMessageConversionException(ServerWebExchange exchange, HttpMessageConversionException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> nullPointerException(ServerWebExchange exchange, NullPointerException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(ClassCastException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> classCastException(ServerWebExchange exchange, ClassCastException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> iOException(ServerWebExchange exchange, IOException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.io.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(NoSuchMethodException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> noSuchMethodException(ServerWebExchange exchange, NoSuchMethodException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "no.such.method", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IndexOutOfBoundsException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> indexOutOfBoundsException(ServerWebExchange exchange, IndexOutOfBoundsException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse();
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> illegalArgumentException(ServerWebExchange exchange, IllegalArgumentException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
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
    public ApiRestResponse<String> bizRuntimeException(ServerWebExchange exchange, BizRuntimeException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.runtime.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BizCheckedException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> bizCheckedException(ServerWebExchange exchange, BizCheckedException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.checked.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BizIOException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> bizIOException(ServerWebExchange exchange, BizIOException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.io.error", ex.getMessage());
            return ApiRestResponse.error(ex.getCode(), message);
        }
        return ApiRestResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(IdempotentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiRestResponse<String> idempotentException(ServerWebExchange exchange, IdempotentException ex) {
        this.logException(ex);
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.idempotent.error", ex.getMessage());
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
    public ApiRestResponse<String> dataAccessException(ServerWebExchange exchange, DataAccessException ex) {
        this.logException(ex);
        Throwable cause = ex.getCause();
        if (cause instanceof SQLSyntaxErrorException) {
            return sqlSyntaxErrorException(exchange, (SQLSyntaxErrorException) ex.getCause());
        } else if (cause instanceof SQLIntegrityConstraintViolationException) {
            return sqlIntegrityConstraintViolationException(exchange, (SQLIntegrityConstraintViolationException) ex.getCause());
        }
        String defaultMessage = "数据库访问异常，请稍后再试";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(BatchUpdateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlBatchUpdateException(ServerWebExchange exchange, BatchUpdateException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据批量更新失败，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlIntegrityConstraintViolationException(ServerWebExchange exchange, SQLIntegrityConstraintViolationException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据保存失败，有重复的数据.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLClientInfoException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlClientInfoException(ServerWebExchange exchange, SQLClientInfoException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库访问异常，客户端配置错误.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLRecoverableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlRecoverableException(ServerWebExchange exchange, SQLRecoverableException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库访问异常，请稍后再试", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLSyntaxErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlSyntaxErrorException(ServerWebExchange exchange, SQLSyntaxErrorException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：SQL 语法错误，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTimeoutException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTimeoutException(ServerWebExchange exchange, SQLTimeoutException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库连接超时，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTransactionRollbackException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTransactionRollbackException(ServerWebExchange exchange, SQLTransactionRollbackException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库错误，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTransientConnectionException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTransientConnectionException(ServerWebExchange exchange, SQLTransientConnectionException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库连接异常，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        log.warn(defaultMessage);
        log.warn("可尝试：1. 增加连接池的大小，2. 检查数据库连接状态，3. 优化SQL查询，4. 调整超时设置");
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLTransientException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlTransientException(ServerWebExchange exchange, SQLTransientException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据服务器繁忙，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlDuplicateKeyException(ServerWebExchange exchange, DataIntegrityViolationException ex) {
        this.logException(ex);
        String defaultMessage = "数据保存失败，有重复的数据.";
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.duplicate.key", defaultMessage);
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }


    /**
     * 500 (Internal Server Error)
     */
    @ExceptionHandler(SQLException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiRestResponse<String> sqlException(ServerWebExchange exchange, SQLException ex) {
        this.logException(ex);
        String defaultMessage = String.format("SQL-%s[%s]：数据库操作失败，请稍后再试.", ex.getSQLState(), ex.getErrorCode());
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.sql.error", defaultMessage);
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
    public ApiRestResponse<String> defaultExceptionHandler(ServerWebExchange exchange, Exception ex) throws Exception {
        this.logException(ex);
        String defaultMessage = ApiCode.SC_INTERNAL_SERVER_ERROR.getReason();
        if (serverI18NProperties.isEnabled()) {
            String message = this.getLocaleMessage(exchange, ex, "sys.error", ex.getMessage());
            return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(message);
        }
        return ApiCode.SC_INTERNAL_SERVER_ERROR.toResponse(defaultMessage);
    }

    /**
     * 异常信息国际化
     */
    protected String getLocaleMessage(ServerWebExchange exchange, Exception ex, String i18nCode, String message) {
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
            Locale locale = exchange.getLocaleContext().getLocale();
            Assert.notNull(locale, "locale must not be null");
            return getMessageSource().getMessage(i18nCode, args, message, locale);
        }
        return message;
    }

}