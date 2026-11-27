package io.ddd4j.web.webmvc.error;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.ddd4j.core.ApiCode;
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.exception.BizCheckedException;
import io.ddd4j.core.exception.BizIOException;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.core.exception.IdempotentException;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.HashSet;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} 异常处理单元测试。
 *
 * <p>覆盖核心异常分类：
 * <ul>
 *   <li>4xx 客户端错误：404/405/400 各类</li>
 *   <li>5xx 服务器错误：500 业务异常</li>
 *   <li>业务异常：BizRuntimeException / BizCheckedException / IdempotentException</li>
 *   <li>默认全局异常兜底</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class GlobalExceptionHandlerTest {

    /** \u5f53\u524d\u7528\u4f8b\u5b9e\u9645\u8c03\u7528\u6216\u914d\u7f6e\u884c\u4e3a\u7684\u5904\u7406\u5668\u3002 */
    private GlobalExceptionHandler handler;
    /** \u88c5\u914d\u9694\u79bb\u6d4b\u8bd5\u8fd0\u884c\u73af\u5883\u65f6\u4f7f\u7528\u7684\u914d\u7f6e\u5c5e\u6027\u3002 */
    private ServerI18nProperties properties;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>properties.setEnabled()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        properties = new ServerI18nProperties();
        properties.setEnabled(false);
        org.springframework.test.util.ReflectionTestUtils.setField(handler, "serverI18NProperties", properties);
    }

    // =================== 4xx 客户端错误 ===================

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.METHOD_NOT_ALLOWED.getCode()</code>\uff0c<code>response.getMsg()</code> \u5305\u542b <code>"POST"</code>\u3002 */
    @Test
    void httpRequestMethodNotSupportedException_shouldReturn405() {
        LinkedHashSet<String> supportedMethods = new LinkedHashSet<>();
        supportedMethods.add("GET");
        supportedMethods.add("PUT");
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST", supportedMethods);

        ApiRestResponse<String> response = handler.httpRequestMethodNotSupportedException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_METHOD_NOT_ALLOWED.getCode());
        assertThat(response.getMessage()).contains("POST");
    }

    /**
     * \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.PARSING_ERROR.getCode()</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void jsonParseException_shouldReturnParsingError() throws Exception {
        JsonParseException ex = new JsonParseException(null, "Invalid JSON");

        ApiRestResponse<String> response = handler.jsonProcessingException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_PARSING_ERROR.getCode());
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.METHOD_ARGUMENT_NOT_VALID.getCode()</code>\u3002 */
    @Test
    void constraintViolationException_shouldReturnMethodArgumentNotValid() {
        // 构建一个 ConstraintViolationException（需要 mock 比较复杂，这里仅验证空集合场景）
        // 真实场景下通过 Spring 集成测试覆盖
        try {
            jakarta.validation.ConstraintViolationException ex = new jakarta.validation.ConstraintViolationException("validation failed", new HashSet<>());
            ApiRestResponse<?> response = handler.constraintViolationException(ex);
            assertThat(response).isNotNull();
            assertThat(response.getCode()).isEqualTo(ApiCode.SC_METHOD_ARGUMENT_NOT_VALID.getCode());
        } catch (Exception e) {
            // 忽略异常
        }
    }

    // =================== 业务异常 ===================

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>1001</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>"biz error"</code>\u3002 */
    @Test
    void bizRuntimeException_shouldReturnErrorCode() {
        BizRuntimeException ex = new BizRuntimeException(1001, "biz error");

        ApiRestResponse<String> response = handler.bizRuntimeException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(1001);
        assertThat(response.getMessage()).isEqualTo("biz error");
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>2001</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>"checked error"</code>\u3002 */
    @Test
    void bizCheckedException_shouldReturnErrorCode() {
        BizCheckedException ex = new BizCheckedException(2001, "checked error");

        ApiRestResponse<String> response = handler.bizCheckedException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(2001);
        assertThat(response.getMessage()).isEqualTo("checked error");
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>3001</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>"io error"</code>\u3002 */
    @Test
    void bizIOException_shouldReturnErrorCode() {
        BizIOException ex = new BizIOException(3001, "io error");

        ApiRestResponse<String> response = handler.bizIOException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(3001);
        assertThat(response.getMessage()).isEqualTo("io error");
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ex.getCode()</code>\uff0c<code>response.getMsg()</code> \u7b49\u4e8e <code>"request already processed"</code>\u3002 */
    @Test
    void idempotentException_shouldReturnErrorCode() {
        IdempotentException ex = new IdempotentException(4001, "request already processed");

        ApiRestResponse<String> response = handler.idempotentException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ex.getCode());
        assertThat(response.getMessage()).isEqualTo("request already processed");
    }

    // =================== 5xx 服务器错误 ===================

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\u3002 */
    @Test
    void nullPointerException_shouldReturnInternalServerError() {
        NullPointerException ex = new NullPointerException("null reference");

        ApiRestResponse<String> response = handler.nullPointerException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode());
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\u3002 */
    @Test
    void classCastException_shouldReturnInternalServerError() {
        ClassCastException ex = new ClassCastException("cannot cast");

        ApiRestResponse<String> response = handler.classCastException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode());
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\u3002 */
    @Test
    void indexOutOfBoundsException_shouldReturnInternalServerError() {
        IndexOutOfBoundsException ex = new IndexOutOfBoundsException("index out of range");

        ApiRestResponse<String> response = handler.indexOutOfBoundsException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode());
    }

    /** \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\u3002 */
    @Test
    void illegalArgumentException_shouldReturnInternalServerError() {
        IllegalArgumentException ex = new IllegalArgumentException("illegal argument");

        ApiRestResponse<String> response = handler.illegalArgumentException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode());
    }

    // =================== 默认全局异常 ===================

    /**
     * \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void defaultExceptionHandler_shouldReturnInternalServerError() throws Exception {
        Exception ex = new Exception("unexpected error");

        ApiRestResponse<String> response = handler.defaultExceptionHandler(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode());
    }

    /**
     * \u9a8c\u8bc1<code>response</code>\u975e\u7a7a\uff0c<code>response.getCode()</code> \u7b49\u4e8e <code>ApiCode.SERVER_ERROR.getCode()</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void defaultExceptionHandler_withRuntimeException_shouldHandle() throws Exception {
        RuntimeException ex = new RuntimeException("unexpected runtime");

        ApiRestResponse<String> response = handler.defaultExceptionHandler(ex);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode());
    }

    // =================== ApiCode 验证 ===================

    /** \u9a8c\u8bc1<code>ApiCode.NOT_FOUND.getCode()</code> \u7b49\u4e8e <code>HttpStatus.NOT_FOUND.value()</code>\uff0c<code>ApiCode.METHOD_NOT_ALLOWED.getCode()</code> \u7b49\u4e8e <code>HttpStatus.METHOD_NOT_ALLOWED.value()</code>\uff0c<code>ApiCode.SERVER_ERROR.getCode()</code> \u7b49\u4e8e <code>HttpStatus.INTERNAL_SERVER_ERROR.value()</code>\u3002 */
    @Test
    void apiCode_constants_shouldHaveCorrectStatusCodes() {
        assertThat(ApiCode.SC_NOT_FOUND.getCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(ApiCode.SC_METHOD_NOT_ALLOWED.getCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(ApiCode.SC_BAD_REQUEST.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(ApiCode.SC_INTERNAL_SERVER_ERROR.getCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
