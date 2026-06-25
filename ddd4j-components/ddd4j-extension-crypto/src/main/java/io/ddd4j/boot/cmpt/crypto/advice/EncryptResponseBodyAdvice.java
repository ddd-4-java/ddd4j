package io.ddd4j.boot.cmpt.crypto.advice;

import cn.hutool.core.lang.ParameterizedTypeImpl;
import cn.hutool.crypto.symmetric.AES;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.boot.cmpt.crypto.domain.annotation.ResponseEncrypt;
import io.ddd4j.boot.core.ApiCode;
import io.ddd4j.boot.core.ApiRestResponse;
import io.ddd4j.boot.core.dto.BaseDTO;
import io.ddd4j.boot.core.exception.CryptoException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 响应内容自动加密
 */
@ControllerAdvice
public class EncryptResponseBodyAdvice implements ResponseBodyAdvice<ApiRestResponse<?>> {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AES aes;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {

        ParameterizedTypeImpl genericParameterType = (ParameterizedTypeImpl) returnType.getGenericParameterType();

        // 如果直接是ApiRestResponse，则返回
        if (genericParameterType.getRawType() == ApiRestResponse.class && returnType.hasMethodAnnotation(ResponseEncrypt.class)) {
            return true;
        }

        if (genericParameterType.getRawType() != ResponseEntity.class) {
            return false;
        }

        // 如果是 ResponseEntity<ApiRestResponse>
        for (Type type : genericParameterType.getActualTypeArguments()) {
            if (((ParameterizedTypeImpl) type).getRawType() == ApiRestResponse.class && returnType.hasMethodAnnotation(ResponseEncrypt.class)) {
                return true;
            }
        }

        return false;
    }

    @SneakyThrows
    @Override
    public ApiRestResponse<?> beforeBodyWrite(ApiRestResponse<?> body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {

        // 待加密对象
        Object data = body.getData();

        // 如果data为空，直接返回
        if (Objects.isNull(data)) {
            return body;
        }

        // 如果是实体，并且继承了BaseDTO，则放入时间戳
        if (data instanceof BaseDTO) {
            ((BaseDTO) data).setCurrentTimeMillis(System.currentTimeMillis());
        }

        // 对象序列化
        String dataText = objectMapper.writeValueAsString(data);

        // 如果data为空，直接返回
        if (StringUtils.isBlank(dataText)) {
            return body;
        }

        // 如果位数小于16，报错
        if (dataText.length() < 16) {
            throw new CryptoException(ApiCode.SC_FAIL, "加密失败，数据小于16位");
        }

        String encryptText = aes.encryptHex(dataText);

        return ApiRestResponse.of(body.getCode(), body.getStatus(), body.getMessage(), encryptText);
    }

}
