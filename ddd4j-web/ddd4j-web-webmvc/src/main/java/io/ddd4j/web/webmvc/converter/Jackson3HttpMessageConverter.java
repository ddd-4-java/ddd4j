package io.ddd4j.web.webmvc.converter;

import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Spring 6 专用 Jackson 3 HttpMessageConverter。
 *
 * <p>替代 {@code MappingJackson2HttpMessageConverter}（要求 Jackson 2 ObjectMapper），
 * 内部直接使用 Jackson 3 {@link ObjectMapper} 进行序列化/反序列化。</p>
 *
 * <p>Spring 7 将原生支持 Jackson 3，届时此类可移除。</p>
 */
public class Jackson3HttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    private final ObjectMapper objectMapper;

    public Jackson3HttpMessageConverter(ObjectMapper objectMapper) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        this.objectMapper = objectMapper;
    }

    @Override
    protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        Charset charset = getContentTypeCharset(outputMessage.getHeaders().getContentType());
        objectMapper.writeValue(
                new OutputStreamWriter(outputMessage.getBody(), charset), object);
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset), clazz);
    }

    @Override
    public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Charset charset = getContentTypeCharset(inputMessage.getHeaders().getContentType());
        return objectMapper.readValue(
                new InputStreamReader(inputMessage.getBody(), charset),
                objectMapper.constructType(type));
    }

    private Charset getContentTypeCharset(@Nullable MediaType contentType) {
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }
}
