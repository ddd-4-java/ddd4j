package io.ddd4j.sample.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver;
import org.springframework.web.server.i18n.LocaleContextResolver;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 原生回退API与实际Boot两种Web上下文中的解析器命名契约。
 */
class SampleReactiveConfigurationContractTest {
    /**
     * 通过 <code>application.run()</code> 取得当前测试需要的结果。
     *
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     * @return 当前测试使用的结果，取自 <code>application.run("--spring.profiles.active=locale-contract", "--server.port=0")</code>
     */
    private static ConfigurableApplicationContext start(WebApplicationType type) {
        SpringApplication application = new SpringApplication(SpringSampleApplication.class);
        application.setWebApplicationType(type);
        return application.run("--spring.profiles.active=locale-contract", "--server.port=0");
    }

    /**
     * 调用 <code>MockServerWebExchange.from()</code>、<code>MockServerHttpRequest.get()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>MockServerWebExchange</code> 结果
     */
    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/locale-contract")
                .header("Accept-Language", "de-DE").header("X-Language", "fr-CA").build());
    }

    /**
     * 验证<code>Modifier.isPublic(SampleReactiveConfiguration.class.getModifiers())</code>为真，<code>Modifier.isPublic(method.getModifiers())</code>为真，<code>resolver.resolveLocaleContext(exchange()).getLocale()</code> 等于 <code>Locale.GERMANY</code>。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void publicApiExposesTheSameNativeAcceptHeaderDefault() throws Exception {
        assertTrue(Modifier.isPublic(SampleReactiveConfiguration.class.getModifiers()));
        Object configuration = SampleReactiveConfiguration.class.getConstructor().newInstance();
        Method method = SampleReactiveConfiguration.class.getMethod("localeContextResolver");
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(LocaleContextResolver.class, method.getReturnType());
        LocaleContextResolver resolver = (LocaleContextResolver) method.invoke(configuration);
        assertEquals(AcceptHeaderLocaleContextResolver.class, resolver.getClass());
        assertEquals(Locale.GERMANY, resolver.resolveLocaleContext(exchange()).getLocale());
    }

    /**
     * 验证<code>context.isActive()</code>为真，<code>selected.getClass().getName()</code> 等于 <code>"org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver"</code>，<code>nativeDefault.resolveLocaleContext(exchange()).getLocale()</code> 等于 <code>Locale.GERMANY</code>。
     */
    @Test
    void realReactiveBootContextKeepsHeaderResolverSelectedAndNativeFallbackSeparate() {
        try (ConfigurableApplicationContext context = start(WebApplicationType.REACTIVE)) {
            assertTrue(context.isActive());
            LocaleContextResolver selected = context.getBean("localeContextResolver", LocaleContextResolver.class);
            assertEquals("org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver",
                    selected.getClass().getName());
            assertEquals(Locale.CANADA_FRENCH, selected.resolveLocaleContext(exchange()).getLocale());
            assertTrue(context.containsBean("nativeWebFluxLocaleContextResolver"), "named native fallback missing");
            assertEquals(2, context.getBeansOfType(LocaleContextResolver.class).size());
            LocaleContextResolver nativeDefault = context.getBean("nativeWebFluxLocaleContextResolver",
                    LocaleContextResolver.class);
            assertEquals(AcceptHeaderLocaleContextResolver.class, nativeDefault.getClass());
            assertNotSame(selected, nativeDefault);
            assertEquals(Locale.GERMANY, nativeDefault.resolveLocaleContext(exchange()).getLocale());
        }
    }

    /**
     * 验证<code>context.isActive()</code>为真，<code>context.containsBean("nativeWebFluxLocaleContextResolver")</code>为假，<code>context.containsBean("sampleReactiveConfiguration")</code>为假。
     */
    @Test
    void servletBootContextDoesNotRegisterReactiveFallback() {
        try (ConfigurableApplicationContext context = start(WebApplicationType.SERVLET)) {
            assertTrue(context.isActive());
            assertFalse(context.containsBean("nativeWebFluxLocaleContextResolver"));
            assertFalse(context.containsBean("sampleReactiveConfiguration"));
        }
    }
}
