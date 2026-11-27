package io.ddd4j.web.webmvc.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.annotation.api.RawResponse;
import io.ddd4j.core.api.IR;
import io.ddd4j.core.api.R;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.web.webmvc.config.BaseWebProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * 全局响应体包装处理器。
 * <p>将所有控制器返回值统一包装为 {@link R} 格式，支持 String 类型特殊处理及 {@link RawResponse} 注解跳过包装。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@RestControllerAdvice
public class GlobalResponseRAdvice implements ResponseBodyAdvice<Object> {
    /**
     * MVC ObjectMapper
     */
    @Autowired
    @Qualifier("mvcObjectMapper")
    ObjectMapper objectMapper;
    /**
     * Web 基础配置属性
     */
    @Autowired
    BaseWebProperties baseWebProperties;

    /**
     * \u68c0\u67e5\u5f53\u524d\u914d\u7f6e\u548c\u63a7\u5236\u5668\u58f0\u660e\u662f\u5426\u5141\u8bb8\u7edf\u4e00\u54cd\u5e94\u5305\u88c5\u3002
     * @param returnType \u5f53\u524d\u63a7\u5236\u5668\u65b9\u6cd5\u7684\u8fd4\u56de\u7c7b\u578b\u4fe1\u606f
     * @param aClass \u5f53\u524d\u6d88\u606f\u4f53\u6240\u4f7f\u7528\u7684\u6d88\u606f\u8f6c\u6362\u5668\u7c7b\u578b
     * @return \u5f53\u524d\u65b9\u6cd5\u5e94\u4f7f\u7528\u7edf\u4e00\u5305\u88c5\u65f6\u8fd4\u56de true
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> aClass) {
        return baseWebProperties.getMvc().getEnableRResponse()
                && !IR.class.isAssignableFrom(returnType.getParameterType())
                && !returnType.hasMethodAnnotation(RawResponse.class);
    }

    /**
     * \u6839\u636e\u8fd4\u56de\u503c\u53ca\u65b9\u6cd5\u58f0\u660e\u4fdd\u7559\u5df2\u6709\u54cd\u5e94\u7ed3\u6784\u6216\u5e94\u7528\u7edf\u4e00\u5305\u88c5\u3002
     * @param data \u5f53\u524d\u51c6\u5907\u4ea4\u7ed9 HTTP \u8f6c\u6362\u5668\u7684\u54cd\u5e94\u4f53\u503c
     * @param returnType \u5f53\u524d\u63a7\u5236\u5668\u65b9\u6cd5\u7684\u8fd4\u56de\u7c7b\u578b\u4fe1\u606f
     * @param mediaType \u5df2\u534f\u5546\u7684\u54cd\u5e94\u5a92\u4f53\u7c7b\u578b
     * @param aClass \u5f53\u524d\u6d88\u606f\u4f53\u6240\u4f7f\u7528\u7684\u6d88\u606f\u8f6c\u6362\u5668\u7c7b\u578b
     * @param serverHttpRequest \u5f53\u524d Spring \u670d\u52a1\u7aef\u8bf7\u6c42
     * @param serverHttpResponse \u5f53\u524d Spring \u670d\u52a1\u7aef\u54cd\u5e94
     * @return \u6700\u7ec8\u5199\u5165 HTTP \u54cd\u5e94\u4f53\u7684\u5bf9\u8c61
     */
    @Override
    public Object beforeBodyWrite(Object data, MethodParameter returnType, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> aClass,
                                  ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        // 兼容声明为 Object 等宽泛类型、但运行时已显式返回 R 的控制器方法。
        if (data instanceof IR) {
            return data;
        }
        if (Objects.isNull(data) || returnType.getParameterType().isAssignableFrom(void.class)) {
            return R.ok();
        }
        if (AggregateRoot.class.isAssignableFrom(returnType.getParameterType())) {
            return R.ok(data);
        }
        // String类型不能直接包装
        if (returnType.getGenericParameterType().equals(String.class)) {
            try {
                //将数据包装在R对象里后转换为json串进行返回
                return objectMapper.writeValueAsString(R.ok(data));
            } catch (JsonProcessingException e) {
                throw new BizRuntimeException(e);
            }
        }
        //否则直接包装成R对象返回
        return R.ok(data);
    }
}
