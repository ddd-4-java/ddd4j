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
    private static ConfigurableApplicationContext start(WebApplicationType type) {
        SpringApplication application = new SpringApplication(SpringSampleApplication.class);
        application.setWebApplicationType(type);
        return application.run("--spring.profiles.active=locale-contract", "--server.port=0");
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/locale-contract")
                .header("Accept-Language", "de-DE").header("X-Language", "fr-CA").build());
    }

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

    @Test
    void servletBootContextDoesNotRegisterReactiveFallback() {
        try (ConfigurableApplicationContext context = start(WebApplicationType.SERVLET)) {
            assertTrue(context.isActive());
            assertFalse(context.containsBean("nativeWebFluxLocaleContextResolver"));
            assertFalse(context.containsBean("sampleReactiveConfiguration"));
        }
    }
}
