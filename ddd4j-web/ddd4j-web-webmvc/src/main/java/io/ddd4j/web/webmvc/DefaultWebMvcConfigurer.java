/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webmvc.webmvc;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import io.ddd4j.extension.jackson.ser.MyBeanSerializerModifier;
import io.ddd4j.web.webmvc.config.LocalResourceProperteis;
import org.springframework.http.MediaType;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.resource.LiteWebJarsResourceResolver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Spring WebMVC 自定义配置器。
 * <p>配置消息转换器（Jackson JSON/日期序列化）、拦截器、静态资源映射等。</p>
 */
public class DefaultWebMvcConfigurer implements WebMvcConfigurer {

    /**
     * 日期时间格式
     */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    /**
     * 日期格式
     */
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    /**
     * 时间格式
     */
    private static final String TIME_PATTERN = "HH:mm:ss";

    private final String META_INF_RESOURCES = "classpath:/META-INF/resources/";
    private final String META_INF_WEBJAR_RESOURCES = META_INF_RESOURCES + "webjars/";

    /**
     * 语言切换拦截器
     */
    private LocaleChangeInterceptor localeChangeInterceptor;
    /**
     * MDC 日志拦截器
     */
    private MdcInterceptor mdcInterceptor;
    /**
     * 本地资源配置
     */
    private LocalResourceProperteis localResourceProperteis;

    public DefaultWebMvcConfigurer(LocalResourceProperteis localResourceProperteis,
                                   LocaleChangeInterceptor localeChangeInterceptor,
                                   MdcInterceptor mdcInterceptor) {
        super();
        this.localResourceProperteis = localResourceProperteis;
        this.localeChangeInterceptor = localeChangeInterceptor;
        this.mdcInterceptor = mdcInterceptor;
    }

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    /**
     * 配置 HTTP 消息转换器。
     * <p>注册 Jackson JSON 转换器（含 Long 转 String、日期时间格式化、自定义序列化修饰器）以及
     * ByteArray、String、Resource、Source、Form 等默认转换器。</p>
     *
     * @param converters 消息转换器列表
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {

        // 指定BigDecimal类型字段使用自定义的CustomDoubleSerialize序列化器
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        simpleModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        simpleModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(DATE_PATTERN);
        simpleModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormat));
        simpleModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormat));

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);
        simpleModule.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
        simpleModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));
        simpleModule.addDeserializer(Date.class, new JsonDeserializer<Date>() {
            @Override
            public Date deserialize(JsonParser p, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                if (Objects.isNull(p)) {
                    return null;
                }
                JsonNode node = p.getCodec().readTree(p);
                if (Objects.isNull(node) || Objects.isNull(node.asText())) {
                    return null;
                }
                return DateUtil.parse(node.asText());
            }
        });

        // 单独初始化ObjectMapper，不使用全局对象，因为下面要指定特殊的输出处理，会影响内部业务逻辑
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .modules(simpleModule, new JavaTimeModule())
                // objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE));
                .simpleDateFormat(DATE_TIME_PATTERN)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .failOnEmptyBeans(false)
                .failOnUnknownProperties(false)
                .featuresToEnable(MapperFeature.USE_GETTERS_AS_SETTERS, MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS).build();

        /** 为objectMapper注册一个带有SerializerModifier的Factory */
        objectMapper.setSerializerFactory(objectMapper.getSerializerFactory().withSerializerModifier(new MyBeanSerializerModifier()));

        //SerializerProvider serializerProvider = objectMapper.getSerializerProvider();
        //serializerProvider.setNullValueSerializer(NullObjectJsonSerializer.INSTANCE);
        MappingJackson2HttpMessageConverter jackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        jackson2HttpMessageConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));
        converters.add(jackson2HttpMessageConverter);
        converters.add(new ByteArrayHttpMessageConverter());
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        converters.add(new ResourceHttpMessageConverter());
        converters.add(new ResourceRegionHttpMessageConverter());
        try {
            converters.add(new SourceHttpMessageConverter<>());
        } catch (Throwable ex) {
            // Ignore when no TransformerFactory implementation is available...
        }
        converters.add(new AllEncompassingFormHttpMessageConverter());
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {

    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mdcInterceptor).addPathPatterns("/**").order(Integer.MIN_VALUE);
        registry.addInterceptor(localeChangeInterceptor).addPathPatterns("/**").order(Integer.MIN_VALUE + 1);
    }

    /**
     * 配置静态资源映射。
     * <p>支持本地文件资源映射、静态目录资源及 WebJars 资源。</p>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地资源映射
        if (!CollectionUtils.isEmpty(localResourceProperteis.getLocalLocations())) {
            Iterator<Entry<String, String>> ite = localResourceProperteis.getLocalLocations().entrySet().iterator();
            while (ite.hasNext()) {
                Entry<String, String> entry = ite.next();
                if (localResourceProperteis.isLocalRelative()) {
                    registry.addResourceHandler(entry.getKey()).addResourceLocations(ResourceUtils.FILE_URL_PREFIX
                            + localResourceProperteis.getLocalStorage() + File.separator + entry.getValue());
                } else {
                    registry.addResourceHandler(entry.getKey()).addResourceLocations(entry.getValue());
                }
            }
        }
        // 指定个性化资源映射
        registry.addResourceHandler("/assets/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/assets/");
        registry.addResourceHandler("/js/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/js/");
        registry.addResourceHandler("/css/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/css/");
        registry.addResourceHandler("/images/**").addResourceLocations(ResourceUtils.CLASSPATH_URL_PREFIX + "/static/images/");
        if (!registry.hasMappingForPattern("/webjars/**")) {
            registry.addResourceHandler("/webjars/**").addResourceLocations(META_INF_WEBJAR_RESOURCES)
                    .resourceChain(false).addResolver(new LiteWebJarsResourceResolver());
        }

    }

}
