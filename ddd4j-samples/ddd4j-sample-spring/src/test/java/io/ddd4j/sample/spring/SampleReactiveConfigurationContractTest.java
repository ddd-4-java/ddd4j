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
     * \u901a\u8fc7 <code>application.run()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>application.run("--spring.profiles.active=locale-contract", "--server.port=0")</code>
     */
    private static ConfigurableApplicationContext start(WebApplicationType type) {
        SpringApplication application = new SpringApplication(SpringSampleApplication.class);
        application.setWebApplicationType(type);
        return application.run("--spring.profiles.active=locale-contract", "--server.port=0");
    }

    /**
     * \u8c03\u7528 <code>MockServerWebExchange.from()</code>\u3001<code>MockServerHttpRequest.get()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>MockServerWebExchange</code> \u7ed3\u679c
     */
    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/locale-contract")
                .header("Accept-Language", "de-DE").header("X-Language", "fr-CA").build());
    }

    /**
     * \u9a8c\u8bc1<code>Modifier.isPublic(SampleReactiveConfiguration.class.getModifiers())</code>\u4e3a\u771f\uff0c<code>Modifier.isPublic(method.getModifiers())</code>\u4e3a\u771f\uff0c<code>resolver.resolveLocaleContext(exchange()).getLocale()</code> \u7b49\u4e8e <code>Locale.GERMANY</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>context.isActive()</code>\u4e3a\u771f\uff0c<code>selected.getClass().getName()</code> \u7b49\u4e8e <code>"org.springframework.extension.web.server.i18n.XHeaderLocaleContextResolver"</code>\uff0c<code>nativeDefault.resolveLocaleContext(exchange()).getLocale()</code> \u7b49\u4e8e <code>Locale.GERMANY</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>context.isActive()</code>\u4e3a\u771f\uff0c<code>context.containsBean("nativeWebFluxLocaleContextResolver")</code>\u4e3a\u5047\uff0c<code>context.containsBean("sampleReactiveConfiguration")</code>\u4e3a\u5047\u3002 */
    @Test
    void servletBootContextDoesNotRegisterReactiveFallback() {
        try (ConfigurableApplicationContext context = start(WebApplicationType.SERVLET)) {
            assertTrue(context.isActive());
            assertFalse(context.containsBean("nativeWebFluxLocaleContextResolver"));
            assertFalse(context.containsBean("sampleReactiveConfiguration"));
        }
    }
}
