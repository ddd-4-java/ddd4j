package io.ddd4j.dropwizard;

import ch.qos.logback.classic.LoggerContext;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logback 升级后默认访问日志、禁用配置与自定义扩展仍保持原有契约。
 */
class Ddd4jLogbackAccessRequestLogFactoryTest {

    @Test
    void replacesDefaultFactoryAndPreservesAppenders() {
        Configuration configuration = new Configuration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory original = (LogbackAccessRequestLogFactory) server.getRequestLogFactory();

        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);

        Ddd4jLogbackAccessRequestLogFactory compatible = assertInstanceOf(
                Ddd4jLogbackAccessRequestLogFactory.class, server.getRequestLogFactory());
        assertNotSame(original, compatible);
        assertSame(original.getAppenders(), compatible.getAppenders());
        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);
        assertSame(compatible, server.getRequestLogFactory());
    }

    @Test
    void keepsDisabledAccessLoggingDisabled() {
        Configuration configuration = new Configuration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory original = new LogbackAccessRequestLogFactory();
        original.setAppenders(List.of());
        server.setRequestLogFactory(original);

        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);

        assertTrue(((LogbackAccessRequestLogFactory) server.getRequestLogFactory()).getAppenders().isEmpty());
        assertEquals(original.isEnabled(), server.getRequestLogFactory().isEnabled());
    }

    @Test
    void preservesCustomRequestLogFactory() {
        Configuration configuration = new Configuration();
        DefaultServerFactory server = (DefaultServerFactory) configuration.getServerFactory();
        LogbackAccessRequestLogFactory custom = new LogbackAccessRequestLogFactory() {
        };
        server.setRequestLogFactory(custom);

        Ddd4jLogbackAccessRequestLogFactory.configure(configuration);

        assertSame(custom, server.getRequestLogFactory());
    }

    @Test
    void startsDefaultLayoutWithoutRemovedClassicConverterMap() {
        LoggerContext context = new LoggerContext();
        Ddd4jLogbackAccessRequestLayout layout = new Ddd4jLogbackAccessRequestLayout(
                context, TimeZone.getTimeZone("UTC"));
        try {
            assertDoesNotThrow(layout::start);
            assertTrue(layout.isStarted());
            assertEquals("%h %l %u [%t{dd/MMM/yyyy:HH:mm:ss Z,UTC}] \"%r\" %s %b "
                    + "\"%i{Referer}\" \"%i{User-Agent}\" %D", layout.getPattern());
        } finally {
            layout.stop();
            context.stop();
        }
    }
}
