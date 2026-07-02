package io.ddd4j.web.webmvc.core;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.spring.util.BeanKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

@RestControllerAdvice
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "==> RequestBody")
public class GlobalRequestAdvice implements RequestBodyAdvice {

    @Override
    public boolean supports(MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) throws IOException {
        return httpInputMessage;
    }

    @Override
    public Object afterBodyRead(Object o, HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        Map<String, Object> objectMap = BeanKit.toMapClean(o);
        String body = JsonKit.toJson(objectMap);
        ThreadContext.set(ContextConstants.REQUEST_BODY, body);
        log.info("{}", body);
        return o;
    }

    @Override
    public Object handleEmptyBody(Object o, HttpInputMessage httpInputMessage, MethodParameter methodParameter, Type type, Class<? extends HttpMessageConverter<?>> aClass) {
        return o;
    }
}
